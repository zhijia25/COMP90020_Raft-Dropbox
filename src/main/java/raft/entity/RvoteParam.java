package raft.entity;

import raft.Consensus;
import lombok.Getter;
import lombok.Setter;

/**
 * 请求投票 RPC 参数.
 *
 * 
 * @see Consensus#requestVote(RvoteParam)
 */
@Getter
@Setter
public class RvoteParam extends BaseParam {

    /** candidate Id(ip:selfPort) */
    String candidateId;

    /** index of last log for candidate */
    long lastLogIndex;

    /** term of last lof for candidate */
    long lastLogTerm;

    private RvoteParam(Builder builder) {
        setTerm(builder.term);
        setServerId(builder.serverId);
        setCandidateId(builder.candidateId);
        setLastLogIndex(builder.lastLogIndex);
        setLastLogTerm(builder.lastLogTerm);
    }

    @Override
    public String toString() {
        return "RvoteParam{" +
            "candidateId='" + candidateId + '\'' +
            ", lastLogIndex=" + lastLogIndex +
            ", lastLogTerm=" + lastLogTerm +
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
        private String candidateId;
        private long lastLogIndex;
        private long lastLogTerm;

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

        public Builder candidateId(String val) {
            candidateId = val;
            return this;
        }

        public Builder lastLogIndex(long val) {
            lastLogIndex = val;
            return this;
        }

        public Builder lastLogTerm(long val) {
            lastLogTerm = val;
            return this;
        }

        public RvoteParam build() {
            return new RvoteParam(this);
        }
    }
}
