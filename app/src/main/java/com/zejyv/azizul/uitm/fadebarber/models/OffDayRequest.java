package com.zejyv.azizul.uitm.fadebarber.models;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class OffDayRequest {
    private String offDayId;
    private String employeeId;
    private String offDateRequest;
    private String startTime;
    private String endTime;
    private boolean wholeDay;
    private String reason;
    private Map<String, Boolean> votes = new HashMap<>(); // uid -> true (accept), false (decline)
    private String status; // "PENDING", "APPROVED", "REJECTED"
    
    @ServerTimestamp
    private Date timestamp;

    public OffDayRequest() {
        this.status = "PENDING";
    }

    public String getOffDayId() { return offDayId; }
    public void setOffDayId(String offDayId) { this.offDayId = offDayId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getOffDateRequest() { return offDateRequest; }
    public void setOffDateRequest(String offDateRequest) { this.offDateRequest = offDateRequest; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public boolean isWholeDay() { return wholeDay; }
    public void setWholeDay(boolean wholeDay) { this.wholeDay = wholeDay; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Map<String, Boolean> getVotes() { return votes; }
    public void setVotes(Map<String, Boolean> votes) { this.votes = votes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
}
