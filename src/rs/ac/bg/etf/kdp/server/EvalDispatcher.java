package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.protocol.EvalAck;
import rs.ac.bg.etf.kdp.protocol.EvalRequest;

/**
 * Apstrakcija koju TupleSpaceServer koristi da prosledi EvalRequest dalje,
 * bez da zna bilo sta o WorkerRegistry/JobRegistry (te klase dolaze u
 * Fazi 5). Prava implementacija (EvalDispatcherImpl, Faza 5) bira slobodnu
 * radnu stanicu i salje joj EvalRequest preko soketa; ovde je samo
 * ugovor da bi Faza 4 mogla da se zavrsi nezavisno.
 */
public interface EvalDispatcher {
    EvalAck dispatch(EvalRequest request);
}