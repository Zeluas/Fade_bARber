package com.zejyv.azizul.uitm.fadebarber.models;

import com.google.firebase.Timestamp;

public class Employee {
    private String uid;
    private String fullname;
    private String phone;
    private String shortname;
    private String specialty;
    private String profilePicUrl;
    private Timestamp joinedDate;
    private double overallRating;

    public Employee() {
        // Required for Firestore
    }

    public Employee(String uid, String fullname, String phone, String shortname, String specialty) {
        this.uid = uid;
        this.fullname = fullname;
        this.phone = phone;
        this.shortname = shortname;
        this.specialty = specialty;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getFullname() { return fullname; }
    public void setFullname(String fullname) { this.fullname = fullname; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getShortname() { return shortname; }
    public void setShortname(String shortname) { this.shortname = shortname; }

    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    public String getProfilePicUrl() { return profilePicUrl; }
    public void setProfilePicUrl(String profilePicUrl) { this.profilePicUrl = profilePicUrl; }

    public Timestamp getJoinedDate() { return joinedDate; }
    public void setJoinedDate(Timestamp joinedDate) { this.joinedDate = joinedDate; }

    public double getOverallRating() { return overallRating; }
    public void setOverallRating(double overallRating) { this.overallRating = overallRating; }
}
