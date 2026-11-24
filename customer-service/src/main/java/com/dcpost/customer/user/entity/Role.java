package com.dcpost.customer.user.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "roles")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Integer id;
    @Column(name = "role_name", nullable = false, unique = true, length = 50)
    private String roleName;

    protected Role() {}

    public Integer getId() { return id; }
    public String getRoleName() { return roleName; }
}
