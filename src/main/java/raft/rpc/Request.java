package raft.rpc;

import java.io.Serializable;

import raft.entity.AentryParam;
import raft.entity.RvoteParam;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import raft.client.ClientKVReq;

/**
 * 
 */
@Getter
@Setter
@ToString
public class Request<T> implements Serializable {

    /** apply for voting */
    public static final int R_VOTE = 0;
    /** appending log */
    public static final int A_ENTRIES = 1;
    /** client */
    public static final int CLIENT_REQ = 2;
    /** configuration change. add*/
    public static final int CHANGE_CONFIG_ADD = 3;
    /** configuration change. remove*/
    public static final int CHANGE_CONFIG_REMOVE = 4;
    /** requst type */
    private int cmd = -1;

    /** param
     * @see AentryParam
     * @see RvoteParam
     * @see ClientKVReq
     * */
    private T obj;

    String url;

    public Request() {
    }

    public Request(T obj) {
        this.obj = obj;
    }

    public Request(int cmd, T obj, String url) {
        this.cmd = cmd;
        this.obj = obj;
        this.url = url;
    }

    private Request(Builder builder) {
        setCmd(builder.cmd);
        setObj((T) builder.obj);
        setUrl(builder.url);
    }

    public static  Builder newBuilder() {
        return new Builder<>();
    }


    public final static class Builder<T> {

        private int cmd;
        private Object obj;
        private String url;

        private Builder() {
        }

        public Builder cmd(int val) {
            cmd = val;
            return this;
        }

        public Builder obj(Object val) {
            obj = val;
            return this;
        }

        public Builder url(String val) {
            url = val;
            return this;
        }

        public Request<T> build() {
            return new Request<T>(this);
        }
    }

}
