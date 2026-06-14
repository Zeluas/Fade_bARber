package com.zejyv.azizul.uitm.fadebarber.models;

import com.google.firebase.Timestamp;

public class Booking {
    private String bookingId;
    private String customerId;
    private String customerName;
    private String employeeId;
    private String employeeName;
    private String date;
    private String time;
    private String hairstyleName;
    private String hairstyleId;
    private String status;
    private Timestamp createdAt;

    public Booking() {
        // Required for Firestore
    }

    public Booking(String bookingId, String customerId, String customerName, String employeeId, String employeeName, String date, String time, String hairstyleName, String hairstyleId, String status, Timestamp createdAt) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.date = date;
        this.time = time;
        this.hairstyleName = hairstyleName;
        this.hairstyleId = hairstyleId;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getHairstyleName() { return hairstyleName; }
    public void setHairstyleName(String hairstyleName) { this.hairstyleName = hairstyleName; }

    public String getHairstyleId() { return hairstyleId; }
    public void setHairstyleId(String hairstyleId) { this.hairstyleId = hairstyleId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
