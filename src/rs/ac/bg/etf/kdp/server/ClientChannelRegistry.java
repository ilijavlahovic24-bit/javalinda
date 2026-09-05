package rs.ac.bg.etf.kdp.server;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Mapira jobId -> red dogadjaja za tu (otvorenu) klijentsku konekciju.
 * register() poziva handleJobSubmission pri prijemu posla; unregister()
 * se poziva kad se konekcija zatvori (posao zavrsen ili ABORTED).
 * publish() koriste HeartbeatMonitor i job-result handler da GURNU
 * dogadjaj ka niti koja stvarno poseduje socket.
 */
public class ClientChannelRegistry {

    private final Map<Long, BlockingQueue<ChannelEvent>> channels = new ConcurrentHashMap<>();

    public BlockingQueue<ChannelEvent> register(long jobId) {
        BlockingQueue<ChannelEvent> queue = new LinkedBlockingQueue<>();
        channels.put(jobId, queue);
        return queue;
    }

    public void unregister(long jobId) {
        channels.remove(jobId);
    }

    /** return false ako kanal ne postoji (klijent vec diskonektovan/posao vec zavrsen) - pozivalac to tretira kao "klijent nedostupan". */
    public boolean publish(long jobId, ChannelEvent event) {
        BlockingQueue<ChannelEvent> queue = channels.get(jobId);
        if (queue == null) {
            return false;
        }
        queue.add(event);
        return true;
    }
}