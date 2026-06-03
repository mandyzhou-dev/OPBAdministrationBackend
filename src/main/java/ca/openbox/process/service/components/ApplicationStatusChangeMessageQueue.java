package ca.openbox.process.service.components;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ApplicationStatusChangeMessageQueue {
    private static final BlockingQueue<LeaveApplicationEmailEvent> queue = new LinkedBlockingQueue<>();

    public static void put(LeaveApplicationEmailEvent event) {
        queue.offer(event);
    }

    public static LeaveApplicationEmailEvent take() throws InterruptedException {
        return queue.take();
    }
}
