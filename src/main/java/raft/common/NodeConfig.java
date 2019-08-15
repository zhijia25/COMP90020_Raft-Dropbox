package raft.common;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class NodeConfig {

    public int selfPort;

    public List<String> peerAddrs;

}
