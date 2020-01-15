class Solution {
    public String convert(String s, int numRows) {
        
        if(numRows == 1){
            return s;
        }
        
        int strlen = s.length();
        
        List<List<Character>> zzl= new ArrayList<>();
        for(int i =0; i<numRows;i++){
            zzl.add(new ArrayList<>());
        } 
        
        int row=0, col=0;
        boolean zig = true;
        
        for(int i=0; i<strlen; i++){
            //zig
            if(zig){
                zzl.get(row).add(s.charAt(i));
                if(row == numRows-1 && numRows == 2){
                    zig = true;
                    row = 0;
                    continue;
                }
                if(row == numRows-1){
                    zig = false;
                }else{
                    row++;
                }
            }            
            //zag
            else{
                row--;
                if(row == 0 && (numRows==1)){
                    zig=true;
                    zzl.get(row).add(s.charAt(i)); 
                }else if(row == 1){
                    zig=true;
                    zzl.get(row).add(s.charAt(i));
                    row--;
                }else{
                    zzl.get(row).add(s.charAt(i));
                }                
            }
            
        }
        
        String x = "";
        for(List<Character> item: zzl){
            for(Character c : item){
                x += c;
            }
        }
        
        return x;
        
    }
}
