package edu.rpi.aris.net.message;

import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;

public class ProofsGetMsg extends Message {

    private final ArrayList<MsgUtil.ProofInfo> proofs = new ArrayList<>();

    public ArrayList<MsgUtil.ProofInfo> getProofs() {
        return proofs;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user) throws SQLException {
        if (!user.userType.equals(NetUtil.USER_INSTRUCTOR))
            return ErrorType.UNAUTHORIZED;
        try (PreparedStatement statement = connection.prepareStatement("SELECT id, name, created_by, created_on FROM proof ORDER BY created_on DESC;")) {
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    String name = rs.getString(2);
                    String createdBy = rs.getString(3);
                    ZonedDateTime createdOn = NetUtil.localToUTC(rs.getTimestamp(4).toLocalDateTime());
                    proofs.add(new MsgUtil.ProofInfo(id, name, createdBy, createdOn));
                }
            }
        }
        return null;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.GET_PROOFS;
    }

    @Override
    public boolean checkValid() {
        for(MsgUtil.ProofInfo info : proofs)
            if(info == null || !info.checkValid())
                return false;
        return true;
    }
}
