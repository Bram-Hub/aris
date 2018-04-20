package edu.rpi.aris.net;

import edu.rpi.aris.net.message.ErrorType;
import edu.rpi.aris.net.message.UserInfoMsg;

import java.io.IOException;
import java.sql.SQLException;

public interface MessageHandler {

    ErrorType getUserInfo(UserInfoMsg msg) throws SQLException, IOException;

}
