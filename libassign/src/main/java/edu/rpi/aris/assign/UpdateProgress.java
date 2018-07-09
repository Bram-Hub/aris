package edu.rpi.aris.assign;

public interface UpdateProgress {

    void setTotalDownloads(double total);

    void setCurrentDownload(double current);

    void setDescription(String description);

}
