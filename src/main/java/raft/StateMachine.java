package raft;

import raft.entity.LogEntry;

/**
 * state machine interface
 */
public interface StateMachine {

    /**
     * apply data to state machine
     *
     * in theory, only need this method, the others are implemented for easily using state machine
     * @param logEntry data in log
     */
    void apply(LogEntry logEntry);

    LogEntry get(String key);

    String getString(String key);

    void setString(String key, String value);

    void delString(String... key);

}
