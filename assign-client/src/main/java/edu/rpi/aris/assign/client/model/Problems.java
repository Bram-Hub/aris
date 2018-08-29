package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.message.ProblemsGetMsg;

public class Problems implements ResponseHandler<ProblemsGetMsg> {
    @Override
    public void response(ProblemsGetMsg message) {

    }

    @Override
    public void onError(boolean suggestRetry, ProblemsGetMsg msg) {

    }
}
