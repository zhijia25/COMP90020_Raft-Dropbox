package raft.membership.changes;

import raft.common.Peer;

/**
 *
 * cluster configuration change port
 *
 * 
 */
public interface ClusterMembershipChanges {

    /**
     * add node
     * @param newPeer
     * @return
     */
    Result addPeer(Peer newPeer);

    /**
     * delete node
     * @param oldPeer
     * @return
     */
    Result removePeer(Peer oldPeer);
}

