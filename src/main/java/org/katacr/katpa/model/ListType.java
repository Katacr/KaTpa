package org.katacr.katpa.model;

/** 定义玩家关系名单的类型。 */
public enum ListType {
    WHITELIST,
    BLACKLIST;

    /** 返回该名单在语言文件中使用的节点名。 */
    public String languageKey() {
        return "list." + name().toLowerCase();
    }
}
