package com.travel.common.constant;

/**
 * 签名验证常量
 */
public class SignatureConstant {

    private SignatureConstant() {}

    /** 签名请求头：应用ID */
    public static final String HEADER_APP_ID = "X-App-Id";

    /** 签名请求头：时间戳 */
    public static final String HEADER_TIMESTAMP = "X-Timestamp";

    /** 签名请求头：签名 */
    public static final String HEADER_SIGN = "X-Sign";

    /** 签名算法 */
    public static final String ALGORITHM = "SHA-256";

    /** 签名有效期（毫秒）：5分钟 */
    public static final long TIMESTAMP_EXPIRE_MILLIS = 5 * 60 * 1000;

    /** Java应用ID */
    public static final String APP_ID_JAVA = "travel-java";

    /** Python应用ID */
    public static final String APP_ID_PYTHON = "travel-python";
}
