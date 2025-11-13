package ca.openbox.resignation.service.components;

import ca.openbox.resignation.entities.ResignationApplication;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ResignationMessageQueue {
    private static final BlockingQueue<ResignationApplication> queue = new LinkedBlockingQueue<>();
    public static void put(ResignationApplication resignationApplication) throws InterruptedException {
        queue.put(resignationApplication);
    }

    public static ResignationApplication take() throws InterruptedException {
        return queue.take();
    }
}
