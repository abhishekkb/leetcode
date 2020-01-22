import java.math.BigInteger;

public class Solution02 {
	
  //using big integer
	public static String solve2(String input) {
		
		int n = Integer.parseInt(input);
		BigInteger factorial = new BigInteger("1");
		for (int i = 0; i < n; i++) {
			factorial = factorial.multiply(new BigInteger(i+""));
		}
		return factorial.toString()	;
		
	}

	private static void main(String[] args) {
		int n =10;
		System.out.println("==================================\n\n"
				+ "factorial of " + n + " = " + solve("" + n)
				+ "=====================================");
		
	}
	
	//using no java api/library
	private static String solve(String input) {
		int n = Integer.parseInt(input);
		
		DLL dll = new DLL();
		dll.tail = new Digit(1);
		
		for (int i = 2; i <= n; i++) {
			multiply(dll, i);
		}
		
		StringBuilder out = new StringBuilder();
		Digit curr = dll.getHead();
		do {
			out.append(curr.data);
			curr = curr.nxt;
		} while (curr.nxt!=null);
		out.append(curr.data);
		
		return out.toString();
	}
	
	private static void multiply(DLL dll, int n) {
		int carry=0;
		Digit curr = dll.tail;
		do {
			int product = curr.data*n + carry;
			if(curr.prev == null) {
				if(product<10) {
					curr.data = product;
				}else {
					addRemainingDigits(curr, product);
				}
			}else {
				carry = product/10;
				curr.data = product%10;
				curr = curr.prev;
			}
		}while(true);
	}
	
	private static void addRemainingDigits(Digit curr, int val) {
		while (val !=0) {
			curr.data = val%10;
			val = val/10;
			if (val==0) {
				break;
			}
			if (curr.prev == null) {
				curr.addPrevious(new Digit(val));
				curr = curr.prev;
			} else {
				curr = curr.prev;
			}
		}
		
	}

	public static class DLL{
		Digit tail;
		
		private Digit getHead() {
			Digit curr = this.tail;
			
			while (curr.prev!=null) {
				curr = curr.prev;
			}
			return curr;
		}
		
	}
	
	public static class Digit{
		int data;
		Digit nxt;
		Digit prev;
		public Digit(int data) {
			this.data = data;
		}
		
		private void addPrevious(Digit prev) {
			this.prev = prev;
			prev.nxt = this;
		}
	}
}
