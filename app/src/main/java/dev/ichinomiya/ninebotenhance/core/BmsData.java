package dev.ichinomiya.ninebotenhance.core;

import java.util.Arrays;
import java.util.Locale;

/** One FC17 reading of the DL BMS; {@code at} is the elapsed-realtime it arrived, 0 for the empty value. */
public record BmsData(String name,int mos,int cells,float capacityAh,float remainingAh,float volts,float amps,int watts,int soc,int[] temps,
                      int maxCellMv,int minCellMv,int avgCellMv,int diffMv,float cycleAh,int cycles,int[] cellMv,long at){
    public static final BmsData EMPTY=new BmsData("",0,0,0,0,0,0,0,-1,new int[0],0,0,0,0,0,0,new int[0],0);
    public BmsData{
        name=name==null?"":name;temps=temps==null?new int[0]:temps.clone();cellMv=cellMv==null?new int[0]:cellMv.clone();
    }
    public boolean known(){return at>0;}
    public boolean charging(){return amps<0;}
    public int temperature(){if(temps.length==0)return Integer.MIN_VALUE;int max=temps[0];for(int t:temps)max=Math.max(max,t);return max;}
    public String describe(){
        return known()?String.format(Locale.ROOT,"SOC %d%% %.1fV %.1fA %dW 剩余 %.1fAh/%.1fAh 压差 %dmV 循环 %d 串数 %d",soc,volts,amps,watts,remainingAh,capacityAh,diffMv,cycles,cells):"无数据";
    }
    @Override public boolean equals(Object o){
        if(this==o)return true;if(!(o instanceof BmsData d))return false;
        return at==d.at&&soc==d.soc&&volts==d.volts&&amps==d.amps&&watts==d.watts&&cycles==d.cycles&&diffMv==d.diffMv&&cells==d.cells&&mos==d.mos
                &&maxCellMv==d.maxCellMv&&minCellMv==d.minCellMv&&avgCellMv==d.avgCellMv&&cycleAh==d.cycleAh&&capacityAh==d.capacityAh&&remainingAh==d.remainingAh
                &&name.equals(d.name)&&Arrays.equals(temps,d.temps)&&Arrays.equals(cellMv,d.cellMv);
    }
    @Override public int hashCode(){return Long.hashCode(at)*31+soc*7+watts;}
}
