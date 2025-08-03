package vc.config.watch.model;

public interface UserWatch extends Watch {
    String watchId();
    String ownerUserId();
    String ownerUserName();
}
