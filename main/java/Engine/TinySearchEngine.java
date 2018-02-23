package Engine;
import se.kth.id1020.util.*;
import se.kth.id1020.TinySearchEngineBase;
import java.util.*;


public class TinySearchEngine implements TinySearchEngineBase {

    Map<Document,List<TinySearchEngine.DocumentWord>> index;
    Map<Document,Integer> wordAmmount;

    private boolean toSort = false;
    private int sortID = -1;
    private int sortDirection = -1;
    String sorting="";

    //Constructing HashMap objects to later be filled.
    public void preInserts(){
        index = new HashMap<Document, List<DocumentWord>>();
        wordAmmount = new HashMap<Document, Integer>();
    }

    //Inserting DocumentWord-objects into HashMaps.
    public void insert(Sentence sentence, Attributes attr) {

      for (Word word: sentence.getWords()) {
          List<DocumentWord> documentWords = index.get(attr.document);


          if (documentWords == null){
              documentWords = new ArrayList<DocumentWord>();
              index.put(attr.document, documentWords);
          }

          //Every word in a document has a specific DocumentWord object.
          //By looking at the DocumentWord attr list's size you can determine the occurrence.

          DocumentWord docword = null;

          for(DocumentWord tempDW : documentWords){
              if(tempDW.word.equals(word.word)){
                  docword = tempDW;
                  break;
              }
          }

          //First time a new word is added
          if(docword == null){
              docword = new DocumentWord(word, attr);
              documentWords.add(docword);
          }

          //The word already exist, add attributes.
          else{
              docword.attributeList.add(attr);
          }

      }
    }

    public void postInserts(){

        //Count the number of words in each document.
        for(Document document: index.keySet()){
            int wordCount = 0;

            for(DocumentWord docwords: index.get(document)){
                wordCount+=docwords.attributeList.size();
            }

            this.wordAmmount.put(document, wordCount);
        }

        //Sorting the words. Necessary??
       /* for (List<DocumentWord> docW : index.values()){
            Collections.sort(docW);
        } */
    }

    public String infix(String strings){
        return "Query : " + prefixToInfix(strings) + sorting;
    }


    // Choose either sorting method or do print unsorted results.
    public List<Document> search(String query) {
        if (query.length() == 0){return null;}

        String[] split = query.split(" ");

        if (split.length > 3 && split[split.length-3].equalsIgnoreCase("ORDERBY")) {
            if (split[split.length - 2].equalsIgnoreCase("relevance")) {
                sortID = 0;
            } else if (split[split.length - 2].equalsIgnoreCase("popularity")) {
                sortID = 1;
            } else {System.out.print("Unknown property");}

            if (split[split.length-1].equalsIgnoreCase("asc")){
                sortDirection=0;
            } else if (split[split.length-1].equalsIgnoreCase("desc")){
                sortDirection=1;
            } else{System.out.print("Unknown direction");}

            toSort = true;
            sorting = " ORDERBY " + (sortID == 0 ? "RELEVANCE" : "POPULARITY") + " " + (sortDirection==0 ? "ASC" : "DESC");

            String queryNew = "";
            for(int i = 0; i < split.length-3; i++){
                queryNew += " "+ split[i];
            }

            query = queryNew.substring(1);

        }

        //Search the query.
        Map<Document, Double> resultsUnsorted = infixSearch(removeParenthesis(prefixToInfix(query)));
        List<Document> results;

        if (toSort)
        {
            results = resultSort(resultsUnsorted);
            if (sortDirection == 1) { Collections.reverse(results); }
        }

        else{
                results = new ArrayList<Document>(resultsUnsorted.keySet());
            }
        return results;
    }

    //Creates a stack to put characters, where the first character is on the top of the stack.
    public String prefixToInfix(String query){
        query = query.trim();

        query = new StringBuilder(query).reverse().toString();

        Stack<Character> stack = new Stack<Character>();

        for (char tmp : query.toCharArray()){
            stack.push(tmp);
        }

        query = prefixToInfix(stack);

        return query;
    }

    //Recursively pops characters from stack and return with parenthesis as infix.
    public String prefixToInfix(Stack<Character> stack){
        if (stack.size() == 0) {return "";}

        char tmp = stack.pop();
        String output = "";

        if(tmp=='+' || tmp=='-' || tmp =='|'){
            output += "(";
            output += prefixToInfix(stack);
            output += tmp;
            output += prefixToInfix(stack);
            output += ")";

        }

        else if (tmp==' '){
            output += prefixToInfix(stack);
        }

        else{
            output += tmp;
            if(stack.size() != 0 && stack.peek()!=' '){
                output += prefixToInfix(stack);
            }
        }
        return output;
    }

    //Recursively searches for words in the query.
    private Map<Document, Double> infixSearch(String query)
    {
        Map<Document,Double> results = new HashMap<Document, Double>();
        String arg1, arg2;
        char opperand;
        String[] split = query.split(" ");

        int depth=0;
            for (int i = 0; i < query.length(); i++) {
                char charCurrent = query.charAt(i);

                if (depth == 0 && (charCurrent == '+' || charCurrent == '-' || charCurrent == '|')) {
                    arg1 = removeParenthesis(query.substring(0, i));
                    arg2 = removeParenthesis(query.substring(i + 1));
                    opperand = query.charAt(i);

                    Map<Document, Double> documentArgument1 = isSingleWord(arg1) ? singleSearch(arg1) : infixSearch(arg1);
                    Map<Document, Double> documentArgument2 = isSingleWord(arg2) ? singleSearch(arg2) : infixSearch(arg2);

                    if (opperand == '+') {
                        results = union(results, intersection(documentArgument1, documentArgument2));
                    } else if (opperand == '-') {
                        results = union(results, difference(documentArgument1, documentArgument2));
                    } else if (opperand == '|') {
                        results = union(results, union(documentArgument1, documentArgument2));
                    }

                }

                if (charCurrent == '(') {
                    depth++;
                } else if (charCurrent == ')') {
                    depth--;
                }
            }

        //If we are searching for one word.
        if(split.length == 1){
            Map<Document,Double> singleQuery = singleSearch(query);
            results = union(results,singleQuery);
        }
        return results;
    }

    //Calculates relevance also searches for a word.
    private Map<Document, Double> singleSearch(String word)
    {
        Map<Document, Double> results = new HashMap<Document, Double>();

        for (Document document : index.keySet())
        {
            for (DocumentWord wordContainer : index.get(document))
            {
                if (wordContainer.word.equals(word))
                {
                    double termFrequency = ((double) wordContainer.attributeList.size()) / ((double) wordAmmount.get(document));
                    results.put(document, termFrequency);
                    break;
                }
            }
        }

        // Apply inverse document frequency
        if (results.size() != 0)
        {
            double inverseDocumentFrequency = Math.log10(index.keySet().size() / results.size());

            for (Document document : results.keySet())
            {
                results.put(document, results.get(document) * inverseDocumentFrequency);
            }
        }

        return results;
    }

    //Chooses between relevance or popularity.
    private List<Document> resultSort(Map<Document, Double> documents)
    {
        List<Document> results = new ArrayList<Document>();

        while (!documents.isEmpty())
        {
            double smallestValue = 0;
            Document smallestDocument = null;

            for (Document document : documents.keySet())
            {
                if (smallestDocument == null || (sortID == 0 && documents.get(document) < smallestValue) || (sortID == 1 && document.popularity < smallestValue))
                {
                    smallestValue = sortID == 0 ? documents.get(document) : document.popularity;
                    smallestDocument = document;
                }
            }

            results.add(smallestDocument);
            //System.out.println("Added " + smallestDocument + " with " + smallestValue);
            documents.remove(smallestDocument);
        }

        return results;
    }

    public Map<Document, Double> intersection(Map<Document, Double> list1, Map<Document, Double> list2)
    {
        Map<Document, Double> list = new HashMap<Document, Double>();

        for (Document document : list1.keySet())
        {
            if (list2.containsKey(document)) { list.put(document, list1.get(document) + list2.get(document)); }
        }

        return list;
    }

    public Map<Document, Double> difference(Map<Document, Double> list1, Map<Document, Double> list2)
    {
        Map<Document, Double> list = new HashMap<Document, Double>();

        for (Document document : list1.keySet())
        {
            if (!list2.containsKey(document)) { list.put(document, list1.get(document)); }
        }

        return list;
    }

    public Map<Document, Double> union(Map<Document, Double> list1, Map<Document, Double> list2)
    {
        Map<Document, Double> list = new HashMap<Document, Double>(list1);

        for (Document document : list2.keySet())
        {
            if (list.containsKey(document)) { list.put(document, list.get(document) + list2.get(document)); }
            else { list.put(document, list2.get(document)); }
        }

        return list;
    }



    //New object where a word is linked to documents.
    public class DocumentWord implements Comparable<DocumentWord> {
       private String word;
       private ArrayList<Attributes> attributeList = new ArrayList<Attributes>();

        DocumentWord(Word word, Attributes attribute){
            this.word = word.word;
            this.attributeList.add(attribute);
        }

        public int compareTo(DocumentWord compareWord){
            return word.compareTo(compareWord.word);
        }
    }


    private String removeParenthesis(String string)
    {
        return string.replaceAll("\\)$|^\\(", "");
    }

    private boolean isSingleWord(String argument){
        return argument.length() - argument.replaceAll("[\\+\\-\\|]", "").length() == 0;
    }


}
