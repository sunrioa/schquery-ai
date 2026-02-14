package cn.ling.crawler.enums;

/**
 * 数据来源类型枚举
 */
public enum SourceTypeEnum {
    MANUAL_UPLOAD(1, "人工上传"),
    CRAWLER_FETCH(2, "爬虫获取"),
    API_IMPORT(3, "API导入");

    private final Integer value;
    private final String description;

    SourceTypeEnum(Integer value, String description) {
        this.value = value;
        this.description = description;
    }

    public Integer getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static SourceTypeEnum fromValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (SourceTypeEnum type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
