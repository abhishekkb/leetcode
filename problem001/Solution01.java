import java.util.ArrayList;
import java.util.List;

public class Solution01{
  public static void main(String[] args){
  
  
  }
  
  
  public static String solve(String input){
    String[] inputs = input.split("\n");
    int rows = Integer.parseInt(inputs[0]);
    
    String myStr = inputs[1];
    if(rows ==1) {
    	return myStr;
    }
    
    List<List<Character>> zigZag = new ArrayList<>();
    for(int i=0;i<rows; i++) {
    	zigZag.add(new ArrayList<>());
    }
    boolean isZig = true;
    
    int r=0;
    
    for (int i = 0; i < myStr.length(); i++) {
		List<Character> rowStr = zigZag.get(r);
		//zig
		if(isZig) {
			if(rows ==2) {
				rowStr.add(myStr.charAt(i));
				r++;
				isZig = false;
			}else {
				rowStr.add(myStr.charAt(i));
				if(r== rows-1) {
					isZig = false;
					r = rows-2;
				}else {
					r++;
				}
			}
		}
		//zag
		else {
			if(rows ==2) {
				rowStr.add(myStr.charAt(i));
				r--;
				isZig = true;
			}else {
				rowStr.add(myStr.charAt(i));
				if(r==1) {
					isZig = true;
				}
				r--;
			}
		}
	}

    StringBuilder outBuilder = new StringBuilder();
    zigZag.forEach(currRow->currRow.forEach(outBuilder::append));
    
	return outBuilder.toString();
  
  }
}

