package com.emb.bs.ite;

public class MoveWithState {
    String move;
    Session.SavedState state;

    public MoveWithState(String move) {
        this.move = move;
    }

    public MoveWithState(String move, Session s) {
        this.move = move;
        if(s!=null) {
            state = s.saveState();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof MoveWithState){
            return move.equals(((MoveWithState) obj).move);
        }else if(obj instanceof String) {
            return move.equals(obj);
        }else{
            return super.equals(obj);
        }
    }

    @Override
    public String toString() {
        return move + " ["+state.toString()+"]";
    }
}
