package com.example.cs.support;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 原生查询返回 {@code Object[]} 时的取值工具。
 *
 * <p>MySQL 驱动对不同列类型返回的 Java 类型并不统一（如整型运算可能返回 Long 或 BigInteger，
 * TINYINT 在 tinyInt1isBit 开启时可能返回 Boolean），
 * 这里统一做防御性转换，避免在 Service 层散布 instanceof 判断。
 */
public final class SqlUtils {

    private SqlUtils() {
    }

    public static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        return Long.valueOf(o.toString());
    }

    public static Integer toInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        return Integer.valueOf(o.toString());
    }

    public static String toStr(Object o) {
        return o == null ? null : o.toString();
    }

    public static BigDecimal toDecimal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(o.toString());
    }

    public static LocalDateTime toDateTime(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (o instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }

    /** 把查询条件归一化为空字符串，便于在 SQL 中用 {@code :param = ''} 做可选过滤。 */
    public static String blankIfNull(String s) {
        return s == null ? "" : s.trim();
    }
}
