package com.zejyv.azizul.uitm.fadebarber.models;

public class Admin {
    private String uid;
    private String fullname;
    private String shortname;
    private String phone;
    private String profilePicUrl;

    public Admin() {
        // Required for Firebase
    }

    public Admin(String uid, String fullname, String shortname, String phone) {
        this.uid = uid;
        this.fullname = fullname;
        this.shortname = shortname;
        this.phone = phone;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getFullname() { return fullname; }
    public void setFullname(String fullname) { this.fullname = fullname; }

    public String getShortname() { return shortname; }
    public void setShortname(String shortname) { this.shortname = shortname; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getProfilePicUrl() { return profilePicUrl; }
    public void setProfilePicUrl(String profilePicUrl) { this.profilePicUrl = profilePicUrl; }
}
