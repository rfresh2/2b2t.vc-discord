package vc.config.watch;

public interface UserWatch extends Watch {
    String watchId();
    String ownerUserId();
    String ownerUserName();
}
