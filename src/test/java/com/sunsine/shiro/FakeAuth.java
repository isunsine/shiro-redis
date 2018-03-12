package com.sunsine.shiro;

import java.io.Serializable;

public class FakeAuth implements Serializable{
    /**  
	 * @Fields serialVersionUID : TODO(用一句话描述这个变量表示什么)  
	 */
	private static final long serialVersionUID = 1L;
	private Integer id;
    private String role;

    public FakeAuth() {}

    public FakeAuth(Integer id, String role) {
        this.id = id;
        this.role = role;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
