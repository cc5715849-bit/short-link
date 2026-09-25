package com.hou.shortlink.link;

/**
 * 62 进制编码工具 —— 短码生成方案的核心（面试必问点）
 *
 * 方案：数据库自增 id + 起始偏移 → 62 进制字符串
 *   短码 = encode(id + 1_000_000_000)
 *
 * 为什么加偏移量：
 *   1e9 落在 62^5(≈9.16亿) 和 62^6(≈568亿) 之间，所以第一条短码就是 6 位，
 *   且后续只会变长不会变短——长度统一、天然不会撞上 /api、/health 这类路由词
 *   （那些词只有 3~6 个字母且含元音组合，而我们的编码空间从 6 位数字字母混合起步）
 *
 * 为什么不用随机串：要靠重试解决冲突，且无法从短码反推 id
 * 为什么不用 MD5 截断：冲突率不可控、长度不稳定
 * 容量：6 位 62 进制约 568 亿条，够用；用尽就加长到 7 位
 */
public class Base62Utils {

    /** 字符表：0-9 a-z A-Z，共 62 个。顺序无所谓，但要固定不变（变了历史短码就全错） */
    private static final char[] ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    /** 起始偏移量：让 id 从 10 亿起步编码，保证短码固定 6 位起步 */
    private static final long ID_OFFSET = 1_000_000_000L;

    private Base62Utils() {
    }

    /** 短码 = encode(自增id + 偏移量) */
    public static String encodeFromId(long id) {
        return encode(id + ID_OFFSET);
    }

    /** 标准 62 进制编码：反复除 62 取余，最后反转 */
    public static String encode(long num) {
        if (num <= 0) {
            throw new IllegalArgumentException("num must be positive: " + num);
        }
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.append(ALPHABET[(int) (num % 62)]);
            num /= 62;
        }
        return sb.reverse().toString();
    }
}
