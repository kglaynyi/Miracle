package com.miracle.kglaynyi.model;

public class CreditPerson {
    private final String name;
    private final String role;
    private final String profilePath;

    public CreditPerson(String name, String role, String profilePath) {
        this.name = name;
        this.role = role;
        this.profilePath = profilePath;
    }

    public String getName() { return name; }
    public String getRole() { return role; }
    public String getProfilePath() { return profilePath; }
}
