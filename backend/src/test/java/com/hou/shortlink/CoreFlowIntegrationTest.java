package com.hou.shortlink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hou.shortlink.link.LinkCacheService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 核心链路集成测试：起完整 Spring 上下文（连真实的 MySQL + Redis），
 * 用 MockMvc 走 HTTP 请求，验证 W3 改造后的完整业务流。
 *
 * 说明：
 *   - 每次测试都用随机用户名注册新用户，重复跑多少遍都不会和库里的旧数据冲突
 *   - 限流测试依赖"新用户计数器从 0 开始"，所以每个测试方法都是独立的新用户
 */
@SpringBootTest
@AutoConfigureMockMvc
class CoreFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    /** 和业务代码用同一个 RedisTemplate（JSON 序列化），直接读业务写的 key */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private LinkCacheService linkCacheService;

    @Test
    @DisplayName("核心链路：注册 → 登录 → 创建 → 两次跳转(302) → 缓存命中 → 统计 PV 增长")
    void fullChain() throws Exception {
        String token = registerAndLogin();
        JsonNode link = createLink(token, "https://example.com/full-chain");
        String shortCode = link.get("shortCode").asText();
        long linkId = link.get("id").asLong();

        // 短码 = Base62(id + 10亿)，固定 6 位起步
        assertEquals(6, shortCode.length());

        String cacheKey = LinkCacheService.KEY_LINK_CODE + shortCode;

        // 第一次跳转：302 到目标网址，同时回填缓存
        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/full-chain"));

        // 第二次跳转前验证：缓存 key 已存在 → 这次走的就是 Redis，不再查库
        assertTrue(redisTemplate.hasKey(cacheKey), "第一次跳转后应回填缓存");
        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isFound());

        // 两次跳转的 pv 都在 Redis 里原子累加了（尚未回写库，这就是"未回写增量"）
        assertTrue(linkCacheService.pendingPv(linkId) >= 2, "跳转后 Redis 里应有 pv 增量");

        // 统计接口：总 pv = 库值 + Redis 未回写增量，必须包含刚才两次跳转
        mockMvc.perform(get("/api/links/" + linkId + "/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pv").value(Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.days").value(7))
                .andExpect(jsonPath("$.data.trend.length()").value(7));
    }

    @Test
    @DisplayName("缓存穿透防护：不存在的短码返回 404，并写入 60 秒空值标记")
    void redirectNonExistent_emptyValueCache() throws Exception {
        // 12 位随机串（真实短码固定 6 位，不会撞上）
        String code = "zz" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        mockMvc.perform(get("/" + code))
                .andExpect(status().isNotFound());

        // 防穿透的关键断言：查不到也要往 Redis 写一个空值标记
        String cacheKey = LinkCacheService.KEY_LINK_CODE + code;
        assertTrue(redisTemplate.hasKey(cacheKey), "空值标记应已写入 Redis");
        assertEquals("NULL", redisTemplate.opsForValue().get(cacheKey));

        // 60 秒内再刷同一个不存在的短码，命中的是空值标记而不是数据库
        mockMvc.perform(get("/" + code))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Cache Aside 写路径：禁用后缓存被删、跳转 404；重新启用后恢复 302")
    void disableEvictsCache() throws Exception {
        String token = registerAndLogin();
        JsonNode link = createLink(token, "https://example.com/evict-demo");
        String shortCode = link.get("shortCode").asText();
        long linkId = link.get("id").asLong();

        // 先跳转一次，把映射装进缓存
        mockMvc.perform(get("/" + shortCode)).andExpect(status().isFound());
        String cacheKey = LinkCacheService.KEY_LINK_CODE + shortCode;
        assertTrue(redisTemplate.hasKey(cacheKey));

        // 禁用：服务里"先更库、再删缓存"
        mockMvc.perform(put("/api/links/" + linkId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertFalse(redisTemplate.hasKey(cacheKey), "禁用后应删除缓存");

        // 缓存已删，回库查询发现被禁用 → 视同不存在，404
        mockMvc.perform(get("/" + shortCode)).andExpect(status().isNotFound());

        // 重新启用（同样会删一次缓存），再跳转恢复 302
        mockMvc.perform(put("/api/links/" + linkId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/evict-demo"));
    }

    @Test
    @DisplayName("限流：同一用户 1 分钟内第 11 次创建短链被拒（4001）")
    void createRateLimit() throws Exception {
        String token = registerAndLogin();
        // 前 10 次：正常创建（新用户计数器从 0 开始，不会误伤）
        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(post("/api/links")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"originUrl\":\"https://example.com/rl-" + i + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
        // 第 11 次：Lua 计数器超过上限，固定窗口 60 秒内被拒
        mockMvc.perform(post("/api/links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originUrl\":\"https://example.com/rl-11\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4001))
                .andExpect(jsonPath("$.message").value("操作太频繁，请稍后再试"));
    }

    // ---------- 测试辅助方法 ----------

    /** 注册随机新用户并登录，返回 JWT token（随机后缀保证可重复执行） */
    private String registerAndLogin() throws Exception {
        String username = "w3test_" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"w3pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"w3pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode data = readData(loginResult);
        return data.get("token").asText();
    }

    /** 创建一条短链，返回响应里的 data 节点 */
    private JsonNode createLink(String token, String originUrl) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originUrl\":\"" + originUrl + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readData(result);
    }

    /** 统一解析 {"code":0,"message":"ok","data":...} 里的 data */
    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data");
    }
}
