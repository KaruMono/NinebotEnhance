package dev.ichinomiya.ninebotenhance.core;

import java.util.*;

/**
 * The BMS card: up to three rows, each holding any number of fields in the saved order; rows without a field are not drawn.
 * The layout is stored as rows separated by "|" with field ids separated by ","; a field may appear in one row only.
 */
public final class BmsCard {
    public static final int SOC=1,VOLTAGE=2,CURRENT=3,POWER=4,CYCLE_AH=5,DIFF=6,CYCLES=7,CELLS=8,REMAINING_AH=9;
    public static final int[] FIELDS={SOC,VOLTAGE,CURRENT,POWER,CYCLE_AH,REMAINING_AH,DIFF,CYCLES,CELLS};
    public static final int MAX_ROWS=3;
    public static final float ROW_HEIGHT=28;
    public record Layout(List<List<Integer>> rows){
        public Layout{rows=normalize(rows);}
        /** Rows that hold at least one field, in order. */
        public List<List<Integer>> visibleRows(){ArrayList<List<Integer>> out=new ArrayList<>();for(List<Integer> row:rows)if(!row.isEmpty())out.add(row);return out;}
        public int rowCount(){return visibleRows().size();}
        /** Card height: one row per non-empty row, at least one for the label or the "not connected" text. */
        public float height(){return Math.max(1,rowCount())*ROW_HEIGHT;}
        public int rowOf(int field){for(int i=0;i<rows.size();i++)if(rows.get(i).contains(field))return i;return -1;}
        /** Fields in display order: row by row. */
        public List<Integer> fields(){ArrayList<Integer> out=new ArrayList<>();for(List<Integer> row:rows)out.addAll(row);return out;}
        public String encode(){
            StringBuilder b=new StringBuilder();
            for(int i=0;i<rows.size();i++){if(i>0)b.append('|');for(int j=0;j<rows.get(i).size();j++){if(j>0)b.append(',');b.append(rows.get(i).get(j));}}
            return b.toString();
        }
    }
    public static final Layout DEFAULT=new Layout(List.of(List.of(SOC,VOLTAGE),List.of(CURRENT,POWER),List.of(CYCLES,DIFF)));
    public static Layout parse(String text){
        if(text==null||text.isEmpty())return DEFAULT;
        ArrayList<List<Integer>> rows=new ArrayList<>();
        for(String part:text.split("\\|",-1)){
            ArrayList<Integer> row=new ArrayList<>();
            for(String id:part.split(","))try{if(!id.trim().isEmpty())row.add(Integer.parseInt(id.trim()));}catch(NumberFormatException ignored){}
            rows.add(row);
        }
        return new Layout(rows);
    }
    static List<List<Integer>> normalize(List<List<Integer>> value){
        ArrayList<List<Integer>> out=new ArrayList<>();HashSet<Integer> seen=new HashSet<>();
        if(value!=null)for(List<Integer> row:value){
            if(out.size()==MAX_ROWS)break;
            ArrayList<Integer> clean=new ArrayList<>();
            if(row!=null)for(Integer f:row)if(f!=null&&known(f)&&seen.add(f))clean.add(f);
            out.add(List.copyOf(clean));
        }
        while(out.size()<MAX_ROWS)out.add(List.of());
        return List.copyOf(out);
    }
    public static boolean known(int field){for(int f:FIELDS)if(f==field)return true;return false;}
    public static String label(int field){
        return switch(field){case SOC->"SOC";case VOLTAGE->"电压";case CURRENT->"电流";case POWER->"功率";case CYCLE_AH->"累计 Ah";case REMAINING_AH->"剩余 Ah";case DIFF->"压差";case CYCLES->"循环次数";case CELLS->"串数";default->"";};
    }
    public static String unit(int field){
        return switch(field){case SOC->"%";case VOLTAGE->"V";case CURRENT->"A";case POWER->"W";case CYCLE_AH->"Ah";case REMAINING_AH->"Ah";case DIFF->"mV";case CYCLES->"次";case CELLS->"串";default->"";};
    }
    /** Value text; "--" when the reading is missing. */
    public static String value(int field,BmsData d){
        if(d==null||!d.known())return "--";
        return switch(field){
            case SOC->d.soc()<0?"--":String.valueOf(d.soc());case VOLTAGE->String.format(Locale.ROOT,"%.1f",d.volts());case CURRENT->String.format(Locale.ROOT,"%.1f",d.amps());
            case POWER->String.valueOf(d.watts());case CYCLE_AH->String.format(Locale.ROOT,"%.1f",d.cycleAh());case REMAINING_AH->String.format(Locale.ROOT,"%.1f",d.remainingAh());case DIFF->String.valueOf(d.diffMv());
            case CYCLES->String.valueOf(d.cycles());case CELLS->String.valueOf(d.cells());default->"";
        };
    }
    /** Sample used by the layout preview. */
    public static final BmsData SAMPLE=new BmsData("BAT3",0x0f,20,30f,26.4f,80.4f,0.3f,24,88,new int[]{21},4032,4010,4024,22,12.5f,5,new int[0],1);
    private BmsCard(){}
}
