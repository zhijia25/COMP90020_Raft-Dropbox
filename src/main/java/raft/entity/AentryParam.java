package raft.entity;

import java.util.Arrays;

import raft.Consensus;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 *
 * 附加日志 RPC 参数. handlerAppendEntries
 *
 * @see Consensus#appendEntries(AentryParam)
 */
@Getter
@Setter
@ToString
public class AentryParam extends BaseParam {

	/** leader's Id，to direct clients'requests from followers to leader以便于跟随者重定向请求 */
    String leaderId;

    /**new log item followed by previous index number  */
    long prevLogIndex;

    /** Term of the prevLog  */
    long preLogTerm;

    /** logs ready to write in（empty when represent heart beat；send more one time for higher efficiency） */
    LogEntry[] entries;

    /** index of log committed  */
    long leaderCommit;

    public AentryParam() {
    }

    private AentryParam(Builder builder) {
        setTerm(builder.term);
        setServerId(builder.serverId);
        setLeaderId(builder.leaderId);
        setPrevLogIndex(builder.prevLogIndex);
        setPreLogTerm(builder.preLogTerm);
        setEntries(builder.entries);
        setLeaderCommit(builder.leaderCommit);
    }

    @Override
    public String toString() {
        return "AentryParam{" +
            "leaderId='" + leaderId + '\'' +
            ", prevLogIndex=" + prevLogIndex +
            ", preLogTerm=" + preLogTerm +
            ", entries=" + Arrays.toString(entries) +
            ", leaderCommit=" + leaderCommit +
            ", term=" + term +
            ", serverId='" + serverId + '\'' +
            '}';
    }

    public static Builder newBuilder() {
        return new Builder();
    }


    public static final class Builder {

        private long term;
        private String serverId;
        private String leaderId;
        private long prevLogIndex;
        private long preLogTerm;
        private LogEntry[] entries;
        private long leaderCommit;

        private Builder() {
        }

        public Builder term(long val) {
            term = val;
            return this;
        }

        public Builder serverId(String val) {
            serverId = val;
            return this;
        }

        public Builder leaderId(String val) {
            leaderId = val;
            return this;
        }

        public Builder prevLogIndex(long val) {
            prevLogIndex = val;
            return this;
        }

        public Builder preLogTerm(long val) {
            preLogTerm = val;
            return this;
        }

        public Builder entries(LogEntry[] val) {
            entries = val;
            return this;
        }

        public Builder leaderCommit(long val) {
            leaderCommit = val;
            return this;
        }

        public AentryParam build() {
            return new AentryParam(this);
        }
    }
}
