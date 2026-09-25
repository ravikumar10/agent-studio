package dev.agentstudio.slack;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/** Renders the bounded Vega-Lite subset produced by Agent Studio without browser dependencies. */
@Component
class ChartPngRenderer {
    private static final Color GREEN=new Color(57,121,92), GRID=new Color(224,232,226), TEXT=new Color(27,38,32);

    byte[] render(Map<?,?> rawChart){
        Map<String,Object> chart=map(rawChart);Map<String,Object> spec=map(chart.get("spec"));List<Map<String,Object>> rows=rows(map(spec.get("data")).get("values"));
        BufferedImage image=new BufferedImage(1200,675,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(Color.WHITE);g.fillRect(0,0,image.getWidth(),image.getHeight());
        g.setColor(TEXT);g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,28));g.drawString(String.valueOf(spec.getOrDefault("title",chart.getOrDefault("title","Agent Studio chart"))),55,55);
        String type=String.valueOf(chart.getOrDefault("chartType","bar"));
        if(type.equals("pie")||type.equals("donut"))pie(g,spec,rows,type.equals("donut"));else cartesian(g,spec,rows,type);
        g.dispose();try(ByteArrayOutputStream output=new ByteArrayOutputStream()){ImageIO.write(image,"png",output);return output.toByteArray();}catch(Exception error){throw new IllegalStateException("Could not render chart image",error);}
    }

    private void cartesian(Graphics2D g,Map<?,?> spec,List<Map<String,Object>> rows,String type){
        int left=90,top=95,width=1040,height=500;g.setColor(GRID);for(int i=0;i<=5;i++){int y=top+i*height/5;g.drawLine(left,y,left+width,y);}g.setColor(TEXT);g.drawLine(left,top,left,top+height);g.drawLine(left,top+height,left+width,top+height);
        Map<?,?> encoding=map(spec.get("encoding"));String xField=field(encoding.get("x")),yField=field(encoding.get("y"));if(rows.isEmpty()||xField.isBlank()||yField.isBlank()){empty(g);return;}
        List<String> folded=foldFields(spec);
        if(!folded.isEmpty()&&(type.equals("bar")||type.equals("grouped-bar"))){groupedBars(g,rows,xField,folded,left,top,width,height);return;}
        if(!folded.isEmpty()&&type.equals("line")){multiLine(g,rows,xField,folded,left,top,width,height);return;}
        double max=rows.stream().mapToDouble(row->number(row.get(yField))).max().orElse(1);if(max<=0)max=1;int step=Math.max(1,width/rows.size());
        Point previous=null;for(int i=0;i<rows.size();i++){Map<String,Object> row=rows.get(i);double value=number(row.get(yField));int x=left+i*step+step/2,y=top+height-(int)Math.round((value/max)*(height-30));
            if(type.equals("line")){g.setStroke(new BasicStroke(4));g.setColor(GREEN);if(previous!=null)g.drawLine(previous.x,previous.y,x,y);g.fill(new Ellipse2D.Double(x-5,y-5,10,10));previous=new Point(x,y);}
            else if(type.equals("scatter")){g.setColor(GREEN);g.fill(new Ellipse2D.Double(x-7,y-7,14,14));}
            else{int bar=Math.max(5,(int)(step*.68));g.setColor(GREEN);g.fillRoundRect(x-bar/2,y,bar,top+height-y,8,8);}
            if(i<12){g.setColor(TEXT);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,13));String label=shortText(row.get(xField));g.drawString(label,Math.max(left,x-g.getFontMetrics().stringWidth(label)/2),top+height+24);}
        }
    }

    private void groupedBars(Graphics2D g,List<Map<String,Object>> rows,String xField,List<String> fields,int left,int top,int width,int height){
        Color[] colors=seriesColors();double max=rows.stream().flatMapToDouble(row->fields.stream().mapToDouble(field->number(row.get(field)))).max().orElse(1);if(max<=0)max=1;
        int group=Math.max(1,width/rows.size()),bar=Math.max(3,(int)(group*.76/fields.size()));
        for(int i=0;i<rows.size();i++){Map<String,Object> row=rows.get(i);int groupStart=left+i*group+(group-fields.size()*bar)/2;
            for(int series=0;series<fields.size();series++){double value=number(row.get(fields.get(series)));int y=top+height-(int)Math.round((value/max)*(height-30));g.setColor(colors[series%colors.length]);g.fillRoundRect(groupStart+series*bar,y,Math.max(2,bar-2),top+height-y,6,6);}
            drawLabel(g,shortText(row.get(xField)),left+i*group+group/2,top+height+24,left);
        }
        drawLegend(g,fields,colors,left+15,top+18);
    }

    private void multiLine(Graphics2D g,List<Map<String,Object>> rows,String xField,List<String> fields,int left,int top,int width,int height){
        Color[] colors=seriesColors();double max=rows.stream().flatMapToDouble(row->fields.stream().mapToDouble(field->number(row.get(field)))).max().orElse(1);if(max<=0)max=1;int step=Math.max(1,width/Math.max(1,rows.size()-1));
        for(int series=0;series<fields.size();series++){Point previous=null;g.setColor(colors[series%colors.length]);g.setStroke(new BasicStroke(4));for(int i=0;i<rows.size();i++){int x=left+i*step;int y=top+height-(int)Math.round((number(rows.get(i).get(fields.get(series)))/max)*(height-30));if(previous!=null)g.drawLine(previous.x,previous.y,x,y);g.fill(new Ellipse2D.Double(x-5,y-5,10,10));previous=new Point(x,y);}}
        for(int i=0;i<rows.size()&&i<12;i++)drawLabel(g,shortText(rows.get(i).get(xField)),left+i*step,top+height+24,left);drawLegend(g,fields,colors,left+15,top+18);
    }

    private void drawLabel(Graphics2D g,String label,int center,int baseline,int left){g.setColor(TEXT);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,13));g.drawString(label,Math.max(left,center-g.getFontMetrics().stringWidth(label)/2),baseline);}
    private void drawLegend(Graphics2D g,List<String> fields,Color[] colors,int x,int y){g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));for(int i=0;i<fields.size()&&i<8;i++){g.setColor(colors[i%colors.length]);g.fillRoundRect(x+i*125,y,15,15,4,4);g.setColor(TEXT);g.drawString(shortText(fields.get(i)),x+21+i*125,y+13);}}
    private static Color[] seriesColors(){return new Color[]{GREEN,new Color(120,174,205),new Color(237,175,83),new Color(199,104,91),new Color(133,113,181)};}

    private static List<String> foldFields(Map<?,?> spec){
        Object transformations=spec.get("transform");if(!(transformations instanceof Collection<?> values))return List.of();
        for(Object value:values){Object fold=map(value).get("fold");if(fold instanceof Collection<?> fields){List<String> result=new ArrayList<>();for(Object field:fields)if(field!=null&&!String.valueOf(field).isBlank())result.add(String.valueOf(field));return List.copyOf(result);}}
        return List.of();
    }

    private void pie(Graphics2D g,Map<?,?> spec,List<Map<String,Object>> rows,boolean donut){
        Map<?,?> encoding=map(spec.get("encoding"));String valueField=field(encoding.get("theta")),labelField=field(encoding.get("color"));double total=rows.stream().mapToDouble(row->Math.max(0,number(row.get(valueField)))).sum();if(total<=0){empty(g);return;}
        Color[] colors={GREEN,new Color(93,160,122),new Color(120,174,205),new Color(237,175,83),new Color(199,104,91),new Color(133,113,181)};int start=0,index=0;
        for(Map<String,Object> row:rows){int angle=(int)Math.round(Math.max(0,number(row.get(valueField)))/total*360);g.setColor(colors[index%colors.length]);g.fillArc(120,120,470,470,start,angle);g.fillRect(670,135+index*38,20,20);g.setColor(TEXT);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,18));g.drawString(shortText(row.get(labelField))+" — "+row.get(valueField),705,152+index*38);start+=angle;index++;if(index>=11)break;}
        if(donut){g.setColor(Color.WHITE);g.fillOval(265,265,180,180);}
    }

    private void empty(Graphics2D g){g.setColor(TEXT);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,22));g.drawString("No chartable data",90,150);}
    private static String field(Object value){return String.valueOf(map(value).getOrDefault("field",""));}
    private static double number(Object value){return value instanceof Number n?n.doubleValue():0;}
    private static String shortText(Object value){String text=String.valueOf(value==null?"":value);return text.length()>22?text.substring(0,21)+"…":text;}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return value instanceof Map<?,?> map?(Map<String,Object>)map:Map.of();}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> rows(Object value){if(!(value instanceof Collection<?> values))return List.of();List<Map<String,Object>> result=new ArrayList<>();for(Object item:values)if(item instanceof Map<?,?> map)result.add((Map<String,Object>)map);return result.subList(0,Math.min(40,result.size()));}
}
