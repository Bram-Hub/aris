package edu.rpi.aris.assign.client.guiold;


import edu.rpi.aris.assign.GradingStatus;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

public class UserInfo extends AssignmentInfo {

    private final int userId;
    private final String name;
    private ArrayList<ProblemInfo> children = new ArrayList<>();
    private GradingStatus gradingStatus = GradingStatus.NONE;
    private String status, date;

    public UserInfo(int userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    @Override
    public Object getColumnData(int columnNum) {
        switch (columnNum) {
            case 0:
                return name;
            case 1:
                return status;
            case 2:
                return date;
            default:
                return null;
        }
    }

    @Override
    public void addChild(AssignmentInfo info) {
//        if (!(info instanceof ProblemInfo))
//            return;
//        children.add((ProblemInfo) info);
//        int correct = 0;
//        boolean warn = false;
//        long timestamp = -1;
//        for (ProblemInfo p : children) {
//            if (p.getGradingStatus() == GradingStatus.CORRECT || p.getGradingStatus() == GradingStatus.CORRECT_WARN) {
//                if (correct == 0)
//                    warn = false;
//                correct++;
//            }
//            if (p.getGradingStatus() == GradingStatus.CORRECT_WARN)
//                warn = true;
//            if (correct == 0 && p.getGradingStatus() == GradingStatus.INCORRECT_WARN)
//                warn = true;
//            timestamp = p.getTimestamp() > timestamp ? p.getTimestamp() : timestamp;
//        }
//        if (correct == children.size())
//            gradingStatus = warn ? GradingStatus.CORRECT_WARN : GradingStatus.CORRECT;
//        else
//            gradingStatus = warn ? GradingStatus.INCORRECT_WARN : GradingStatus.INCORRECT;
//        status = correct + "/" + children.size() + (warn ? " (Warning)" : "");
//        date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(timestamp));
    }

    @Override
    public ArrayList<ProblemInfo> getChildren() {
        return children;
    }

    @Override
    public int compareTo(AssignmentInfo o) {
        return 0;
    }

    public int getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

}
