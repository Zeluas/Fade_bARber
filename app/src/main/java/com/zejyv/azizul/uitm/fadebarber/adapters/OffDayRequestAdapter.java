package com.zejyv.azizul.uitm.fadebarber.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.zejyv.azizul.uitm.fadebarber.R;
import com.zejyv.azizul.uitm.fadebarber.models.OffDayRequest;
import java.util.List;
import java.util.Map;

public class OffDayRequestAdapter extends RecyclerView.Adapter<OffDayRequestAdapter.ViewHolder> {

    private final List<OffDayRequest> requests;
    private final OnRequestActionListener listener;
    private final String currentUserId;
    private final boolean isAdmin;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface OnRequestActionListener {
        void onVote(OffDayRequest request, boolean accept);
        void onAdminDecision(OffDayRequest request, boolean approve);
    }

    public OffDayRequestAdapter(List<OffDayRequest> requests, String currentUserId, boolean isAdmin, OnRequestActionListener listener) {
        this.requests = requests;
        this.currentUserId = currentUserId;
        this.isAdmin = isAdmin;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_employee_off_day, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OffDayRequest request = requests.get(position);
        
        // Reset dynamic fields to avoid showing old data during recycling
        holder.tvEmployeeName.setText("Loading...");
        holder.tvEmployeeUsername.setText("...");
        holder.tvEmployeeUid.setText("UID: " + request.getEmployeeId());
        holder.ivProfileImage.setImageResource(R.drawable.ic_profile);

        // Dynamic Data Fetching
        fetchEmployeeDetails(request.getEmployeeId(), holder);

        String timeStr = request.isWholeDay() ? "Whole Day" : request.getStartTime() + " - " + request.getEndTime();
        holder.tvDate.setText(request.getOffDateRequest());
        holder.tvTime.setText(timeStr);
        
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            holder.etReason.setText("none reason given...");
            holder.etReason.setTypeface(null, android.graphics.Typeface.ITALIC);
        } else {
            holder.etReason.setText(request.getReason());
            holder.etReason.setTypeface(null, android.graphics.Typeface.NORMAL);
        }

        Map<String, Boolean> votes = request.getVotes();
        int accepts = 0;
        int declines = 0;
        if (votes != null) {
            for (Boolean v : votes.values()) {
                if (v) accepts++; else declines++;
            }
        }
        holder.tvVoteAcceptCount.setText(accepts + " Accept");
        holder.tvVoteDeclineCount.setText(declines + " Decline");

        holder.tvStatusBadge.setVisibility(View.GONE);
        holder.llVoteActions.setVisibility(View.GONE);
        holder.llAdminActions.setVisibility(View.GONE);
        holder.tvUserVoteStatus.setVisibility(View.GONE);

        if (!"PENDING".equals(request.getStatus())) {
            holder.tvStatusBadge.setVisibility(View.VISIBLE);
            holder.tvStatusBadge.setText(request.getStatus());
            holder.tvStatusBadge.setBackgroundResource("APPROVED".equals(request.getStatus()) ? 
                    R.drawable.bg_vote_accept_badge : R.drawable.bg_vote_decline_badge);
        } else {
            if (isAdmin) {
                holder.llAdminActions.setVisibility(View.VISIBLE);
            } else if (!request.getEmployeeId().equals(currentUserId)) {
                if (votes != null && votes.containsKey(currentUserId)) {
                    holder.tvUserVoteStatus.setVisibility(View.VISIBLE);
                    boolean votedAccept = votes.get(currentUserId);
                    holder.tvUserVoteStatus.setText(votedAccept ? "You Accepted" : "You Declined");
                    holder.tvUserVoteStatus.setTextColor(votedAccept ? 0xFF4CAF50 : 0xFFF44336);
                } else {
                    holder.llVoteActions.setVisibility(View.VISIBLE);
                }
            }
        }

        holder.btnVoteAccept.setOnClickListener(v -> listener.onVote(request, true));
        holder.btnVoteDecline.setOnClickListener(v -> listener.onVote(request, false));
        holder.btnAdminApprove.setOnClickListener(v -> listener.onAdminDecision(request, true));
        holder.btnAdminReject.setOnClickListener(v -> listener.onAdminDecision(request, false));
    }

    private void fetchEmployeeDetails(String uid, ViewHolder holder) {
        // First check employees, then admins
        db.collection("employees").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                holder.tvEmployeeName.setText(doc.getString("fullname"));
                holder.tvEmployeeUsername.setText("@" + doc.getString("shortname"));
            } else {
                db.collection("admins").document(uid).get().addOnSuccessListener(adminDoc -> {
                    if (adminDoc.exists()) {
                        holder.tvEmployeeName.setText(adminDoc.getString("fullname"));
                        holder.tvEmployeeUsername.setText("@" + adminDoc.getString("shortname"));
                    }
                });
            }
        });

        db.collection("profile_pics").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String url = doc.getString("url");
                if (url != null && !url.isEmpty()) {
                    Glide.with(holder.itemView.getContext())
                            .load(url)
                            .placeholder(R.drawable.ic_profile)
                            .error(R.drawable.ic_profile)
                            .into(holder.ivProfileImage);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvEmployeeName, tvEmployeeUsername, tvEmployeeUid, tvDate, tvTime, tvVoteAcceptCount, tvVoteDeclineCount, tvUserVoteStatus, tvStatusBadge;
        TextInputEditText etReason;
        ImageView ivProfileImage;
        View llVoteActions, llAdminActions, btnVoteAccept, btnVoteDecline, btnAdminApprove, btnAdminReject;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmployeeName = itemView.findViewById(R.id.tv_item_employee_name);
            tvEmployeeUsername = itemView.findViewById(R.id.tv_item_employee_username);
            tvEmployeeUid = itemView.findViewById(R.id.tv_item_employee_uid);
            ivProfileImage = itemView.findViewById(R.id.iv_item_profile_image);
            tvDate = itemView.findViewById(R.id.tv_item_off_date);
            tvTime = itemView.findViewById(R.id.tv_item_off_time);
            etReason = itemView.findViewById(R.id.et_item_off_reason);
            tvVoteAcceptCount = itemView.findViewById(R.id.tv_vote_accept_count);
            tvVoteDeclineCount = itemView.findViewById(R.id.tv_vote_decline_count);
            tvUserVoteStatus = itemView.findViewById(R.id.tv_user_vote_status);
            tvStatusBadge = itemView.findViewById(R.id.tv_item_status_badge);
            llVoteActions = itemView.findViewById(R.id.ll_vote_actions);
            llAdminActions = itemView.findViewById(R.id.ll_admin_actions);
            btnVoteAccept = itemView.findViewById(R.id.btn_vote_accept);
            btnVoteDecline = itemView.findViewById(R.id.btn_vote_decline);
            btnAdminApprove = itemView.findViewById(R.id.btn_admin_approve);
            btnAdminReject = itemView.findViewById(R.id.btn_admin_reject);
        }
    }
}
