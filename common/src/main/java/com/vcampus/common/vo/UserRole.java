package com.vcampus.common.vo;

/**
 * 系统用户角色。
 *
 * @author xingyi852
 */
public enum UserRole { // 角色枚举
    ADMIN("系统管理员", 0),
    ACADEMIC_AFFAIRS_TEACHER("教务老师", 1),
    LIBRARIAN("图书管理员", 2),
    STORE_MANAGER("商店管理员", 3),
    TEACHER("教职工", 4),
    STUDENT("学生", 5);

    private final String label;
    private final int jurisdiction;

    UserRole(String label, int jurisdiction) {
        this.label = label;
        this.jurisdiction = jurisdiction;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 获取该角色对应的权限编号。
     */
    public int getJurisdiction() {
        return jurisdiction;
    }

    /**
     * 根据参考项目的权限编号反向查找角色。
     *
     * @param jurisdiction 权限编号
     * @return 对应角色，未匹配时返回 null
     */
    public static UserRole fromJurisdiction(int jurisdiction) {
        for (UserRole role : values()) {
            if (role.jurisdiction == jurisdiction) {
                return role;
            }
        }
        return null;
    }
}
