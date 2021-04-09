# DFA parser, test project for JetBrains 2021 internship program

This project contains a simple parser to find
redundant assignments for an imaginary language

### The syntax rules are:

    program ::= statement_list
    statement_list ::= statement | statement_list statement
    statement ::= variable '=' expression | 'if' expression statement_list 'end' | 'while' expression statement_list 'end'   
    expression ::= variable | constant | '(' expression ')' | expression operator expression
    operator ::= '+' | '-' | '*' | '/' | '<' | '>' 

### How to use:
Output the results to console:

    ./gradlew run --args='<inputFilePath>'

Output the results to a file:

    ./gradlew run --args='<inputFilePath> --output "outputFilePath"

Example to run:
    
    ./gradlew run --args='src/test/resources/example.txt --output out.txt'
    
Run tests with:

    ./gradlew test



More example inputs can be found in */src/test/resources* folder,
with .txt extension.

### Non-obvious Implementation details

* Program does not evaluate expressions. Therefore
  it considers every **if** branch
  to be reachable, and every **while** branch to be
  reachable at least twice
  
* Program handles situations when an assignment occurs
  inside a **while** branch and that variable can later
  be read in a higher line of the same branch
  
* Program supports unconventional spacing including
spreading an expression across multiple lines. However,
  in the output it always gives the assignments in a 
  prettified format (all symbols and keywords except
  for brackets are separated with spaces)
