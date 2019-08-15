package raft;

import raft.entity.AentryParam;
import raft.entity.AentryResult;
import raft.entity.RvoteParam;
import raft.entity.RvoteResult;

/**
 * Raft consensus modules.
 */
public interface Consensus {

    /**
     * apply fo voting, by RPC
     *
     * reciever of voting：
     *
     *      if term < currentTerm then reruen false 
     *      if votedFor is empty or  candidateId，and the log of candidate is up-to-date as itself，then vote for him
     * @return
     */
    RvoteResult requestVote(RvoteParam param);

    /**
     * appending log, to increase efficiency, by RPC
     *
     * receiver：
     *
     *    if term < currentTerm then return false 
     *    if the term of the item at prevLogIndex != prevLogTerm, returnfalse
     *    if there is a conflict between exsuting items and new ones（index same but term different），then delete this item and all ones following
     *    add items which are not in log
     *    if leaderCommit > commitIndex， commitIndex is the min of leaderCommit and index of new log
     * @return
     */
    AentryResult appendEntries(AentryParam param);


}
