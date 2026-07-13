package com.zejyv.azizul.uitm.fadebarber.models;

public class Customer {
    private String uid;
    private String name;
    private String username;
    private String phone;
    private String profilePicUrl;

    public Customer() {
        // Required for Firebase
    }

    public Customer(String uid, String name, String username, String phone) {
        this.uid = uid;
        this.name = name;
        this.username = username;
        this.phone = phone;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getProfilePicUrl() { return profilePicUrl; }
    public void setProfilePicUrl(String profilePicUrl) { this.profilePicUrl = profilePicUrl; }
}
