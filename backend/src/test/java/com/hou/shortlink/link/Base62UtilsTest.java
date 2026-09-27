package com.hou.shortlink.link;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Base62Utils 纯单元测试：不依赖 Spring / MySQL / Redis，直接 new 就能跑
 * 测试分层的第一层——工具类用最便宜的方式测
 */
class Base62UtilsTest {

    @Test
    @DisplayName("小数字编码：逢 62 进位（62 → 10）")
    void encode_smallNumbers() {
        assertEquals("1", Base62Utils.encode(1));
        assertEquals("9", Base62Utils.encode(9));
        assertEquals("a", Base62Utils.encode(10));   // 10 号字符是 'a'
        assertEquals("z", Base62Utils.encode(35));   // 小写字母段的最后一个
        assertEquals("Z", Base62Utils.encode(61));   // 字符表收尾是大写 'Z'
        assertEquals("10", Base62Utils.encode(62));  // 满 62 进位，类似十进制的 9→10
        assertEquals("11", Base62Utils.encode(63));
    }

    @Test
    @DisplayName("encodeFromId：加 10 亿偏移后，前 550 亿条短码固定 6 位")
    void encodeFromId_sixChars() {
        // 1e9 落在 62^5(≈9.16亿) 和 62^6(≈568亿) 之间 → 第一条短码就是 6 位
        assertEquals(6, Base62Utils.encodeFromId(1).length());
        assertEquals(6, Base62Utils.encodeFromId(0).length());
        // id = 550 亿时仍未超过 62^6，长度保持 6 位不变
        assertEquals(6, Base62Utils.encodeFromId(55_000_000_000L).length());
    }

    @Test
    @DisplayName("非正数直接抛异常（防御性编程）")
    void encode_nonPositive_throws() {
        assertThrows(IllegalArgumentException.class, () -> Base62Utils.encode(0));
        assertThrows(IllegalArgumentException.class, () -> Base62Utils.encode(-1));
    }
}
