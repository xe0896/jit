public class Main {
    public static void main(String[] args) {
        switch(args[0]) {
            case "commit" -> System.out.println("you commited");
            case "add" -> System.out.println("you added");
            case "push" -> System.out.println("you pushed");
            case "pull" -> System.out.println("you pulled");
            default -> System.out.println("idk");
        }
    }
}
