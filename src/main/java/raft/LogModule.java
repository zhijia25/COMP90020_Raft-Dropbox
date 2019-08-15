package raft;

import raft.entity.LogEntry;

/**
 *
 * @see raft.entity.LogEntry
 */
public interface LogModule {

    void write(LogEntry logEntry);

    LogEntry read(Long index);

    void removeOnStartIndex(Long startIndex);

    LogEntry getLast();

    Long getLastIndex();
}
