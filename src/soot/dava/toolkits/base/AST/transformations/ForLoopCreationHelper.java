/* Soot - a J*va Optimization Framework
 * Copyright (C) 2005 Nomair A. Naeem
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

package soot.dava.toolkits.base.AST.transformations;

import java.util.*;
import soot.*;
import soot.jimple.*;
import soot.dava.*;
import soot.dava.internal.SET.*;
import soot.dava.internal.asg.*;
import soot.dava.internal.AST.*;
import soot.util.*;

public class ForLoopCreationHelper{

    ASTStatementSequenceNode stmtSeqNode;
    ASTWhileNode whileNode;

    ASTStatementSequenceNode newStmtSeqNode;
    ASTForLoopNode forNode;

    Map varToStmtMap;

    public ForLoopCreationHelper(ASTStatementSequenceNode stmtSeqNode, ASTWhileNode whileNode){
	this.stmtSeqNode=stmtSeqNode;
	this.whileNode=whileNode;
	varToStmtMap = new HashMap();
    }


    /*
      The purpose of this method is to replace the statement sequence node
      given by the var nodeNumber with the new statement sequence node
      and to replace the next node (which sould be a while node
      with the for loop node

      The new body is then returned;

    */
    public List createNewBody(List oldSubBody, int nodeNumber){
	List newSubBody = new ArrayList();

	if(oldSubBody.size()<= nodeNumber){
	    //something is wrong since the oldSubBody has lesser nodes than nodeNumber
	    return null;
	}

	Iterator oldIt = oldSubBody.iterator();
	int index=0;
	while(index!=nodeNumber){
	    newSubBody.add(oldIt.next());
	    index++;
	}

	//check to see that the next is a stmtseq and the one afteris while node
	ASTNode temp = (ASTNode)oldIt.next();
	if(!(temp instanceof ASTStatementSequenceNode))
	    return null;
	temp = (ASTNode)oldIt.next();
	if(!(temp instanceof ASTWhileNode))
	    return null;
	
	//add new stmtseqnode to the newSubBody
	if(newStmtSeqNode!=null){
	    newSubBody.add(newStmtSeqNode);
	}
	else{
	    //System.out.println("Stmt seq was empty hence not putting a node in");
	}

	//add new For Loop Node
	newSubBody.add(forNode);


	//copy any remaining nodes
	while(oldIt.hasNext()){
	    newSubBody.add(oldIt.next());
	}
	
	return newSubBody;
    }



    /*
      Go through the stmtseq node
      and collect all defs

      Important: if a def is followed by a non def stmt
      clear def list and continue 

      i.e. we are conservatively checking when a def can be 
      moved into a for loop body
    */
    private List getDefs(){
	if(stmtSeqNode==null){
	    return null;
	}
	
	List toReturn= new ArrayList();

	List statements = stmtSeqNode.getStatements();
	Iterator stmtIt = statements.iterator();
	int stmtNum=0;
	while(stmtIt.hasNext()){
	    AugmentedStmt as = (AugmentedStmt)stmtIt.next();
	    Stmt s = as.get_Stmt();
	    
	    //check if this is a def
	    if(s instanceof DefinitionStmt){
		Value left = ((DefinitionStmt)s).getLeftOp();
		toReturn.add(left.toString());
		varToStmtMap.put(left.toString(),new Integer(stmtNum));
	    }
	    else{
		toReturn = new ArrayList();
		varToStmtMap = new HashMap();
	    }
	    stmtNum++;
	}//going through all statements
	return toReturn;
    }

    /*
      Go through the ASTCondition of the whileNode
      Make a list of all vars being uses in the conditions
      Since any of them could be being used to drive the loop
    */
    private List getCondUses(){
	if(whileNode == null){
	    return null;
	}
	ASTCondition cond = whileNode.get_Condition();

	return getCond(cond);
    }

    private List getCond(ASTCondition cond){
	List toReturn=new ArrayList();
	
	if(cond instanceof ASTUnaryCondition){
	    toReturn.add(((ASTUnaryCondition)cond).toString());
	}
	else if (cond instanceof ASTBinaryCondition){
	    ConditionExpr condExpr = ((ASTBinaryCondition)cond).getConditionExpr();
	    toReturn.add(condExpr.getOp1().toString());
	    toReturn.add(condExpr.getOp2().toString());
	}
	else if (cond instanceof ASTAggregatedCondition){
	    toReturn.addAll(getCond(((ASTAggregatedCondition)cond).getLeftOp()));
	    toReturn.addAll(getCond(((ASTAggregatedCondition)cond).getRightOp()));
	}
	return toReturn;

    }



    private List getCommonVars(List defs, List condUses){

	List toReturn = new ArrayList();
	Iterator defIt = defs.iterator();

	while(defIt.hasNext()){
	    String defString = (String)defIt.next();
	    Iterator condIt = condUses.iterator();
	    while(condIt.hasNext()){
		String condString = (String)condIt.next();
		
		if(condString.compareTo(defString)==0){
		    //match
		    toReturn.add(defString);
		    break;
		}
	    }
	}

	return toReturn;
    }


    /*
      Given the StmtSequenceNode and the while Node
      Check if the while can be converted to a for
      
      If this can be done. create the replacement stmt sequence node
      and the new for loop and return TRUE;

      else return FALSE;
    */
    public boolean checkPattern(){
	List defs = getDefs();
	if(defs==null){
	    return false;
	}
	if(defs.size()==0){
	    return false;
	}

	List condUses = getCondUses();
	if(condUses==null){
	    return false;
	}
	if(condUses.size()==0){
	    return false;
	}

	/*
	  find common vars between the defs and the condition
	*/
	List commonVars = getCommonVars(defs,condUses);

	/*
	  Find the update list
	  Also at the same time see if the update list has
	  some update stmt whose var should be added to commonVars
	*/
	List update = getUpdate(defs,condUses,commonVars);
	if(update==null){
	    return false;
	}
	if(update.size()==0){
	    return false;
	}



	if(commonVars==null){
	    return false;
	}

	if(commonVars.size()==0){
	    return false;
	}
	
	//there are some vars which are 
	//1, defined in the stmtseq node
	//2, used in the condition
	//System.out.println(commonVars);



	//create new stmtSeqNode and get the init list for the for loop
	List init= createNewStmtSeqNodeAndGetInit(commonVars);
	if(init.size()==0){
	    return false;
	}



	ASTCondition condition = whileNode.get_Condition();
	List body = (List)whileNode.get_SubBodies().get(0);
	SETNodeLabel label = ((ASTLabeledNode)whileNode).get_Label();



	/*
	  Check that anything in init is not a first time initialization
	  if it is and it is not used outside the for loop then 
	  we need to declare it as int i = bla bla instead of i = bla bla
	*/
	//init=analyzeInit(init);


	forNode = new ASTForLoopNode(label,init,condition,update,body);
	return true;
    }




    private List getUpdate(List defs,List condUses, List commonUses){
	List toReturn = new ArrayList();

	//most naive approach
	List subBodies = whileNode.get_SubBodies();
	if(subBodies.size()!=1){
	    //whileNode should always have oneSubBody
	    return toReturn;
	}

	List subBody = (List)subBodies.get(0);
	Iterator it = subBody.iterator();
	while(it.hasNext()){
	    ASTNode temp = (ASTNode)it.next();
	    if(!(it.hasNext())){
		if(temp instanceof ASTStatementSequenceNode){
		    List stmts = ((ASTStatementSequenceNode)temp).getStatements();
		    AugmentedStmt last = (AugmentedStmt)stmts.get(stmts.size()-1);
		    Stmt lastStmt = last.get_Stmt();
		    if(lastStmt instanceof DefinitionStmt){
			//check if it defines a def
			
			Value left = ((DefinitionStmt)lastStmt).getLeftOp();
			Iterator defIt = defs.iterator();
			while(defIt.hasNext()){
			    String defString = (String)defIt.next();
			    if(left.toString().compareTo(defString)==0){
				//match
				toReturn.add(last);
				
				stmts.remove(stmts.size()-1);

				//see if commonUses has this otherwise add it
				Iterator coIt = commonUses.iterator();
				boolean matched=false;
				while(coIt.hasNext()){
				    if (defString.compareTo((String)coIt.next())==0){
					matched=true;
				    }
				}
				if(!matched){
				    //it is not in commonUses
				    commonUses.add(defString);
				}
				
				return toReturn;
			    }
			}

			Iterator condIt = condUses.iterator();
			while(condIt.hasNext()){
			    String condString = (String)condIt.next();
			    if(left.toString().compareTo(condString)==0){
				//match
				toReturn.add(last);

				stmts.remove(stmts.size()-1);
				
				//see if commonUses has this otherwise add it
				Iterator coIt = commonUses.iterator();
				boolean matched=false;
				while(coIt.hasNext()){
				    if (condString.compareTo((String)coIt.next())==0){
					matched=true;
				    }
				}
				if(!matched){
				    //it is not in commonUses
				    commonUses.add(condString);
				}
				return toReturn;
			    }
			}



		    }
		}//the last node is a stmtSeqNode
	    }//there is no next node
	}//going through ASTNodes

	return toReturn;
    }


    private List createNewStmtSeqNodeAndGetInit(List commonVars){
	//get stmt number of each def of commonVar keeping the lowest
	int currentLowestPosition=999;
	Iterator it = commonVars.iterator();
	while(it.hasNext()){
	    String temp = (String)it.next();
	    Integer tempInt = (Integer)varToStmtMap.get(temp);
	    if(tempInt !=null){
		if(tempInt.intValue()<currentLowestPosition){
		    currentLowestPosition=tempInt.intValue();
		}
	    }
	}

	List stmts = new ArrayList();
	
	
	List statements = stmtSeqNode.getStatements();
	Iterator stmtIt = statements.iterator();
	int stmtNum=0;

	while(stmtNum<currentLowestPosition   && stmtIt.hasNext()){
	    stmts.add(stmtIt.next());
	    stmtNum++;
	}

	if(stmts.size()>0){
	    newStmtSeqNode = new ASTStatementSequenceNode(stmts);
	}
	else{
	    newStmtSeqNode = null;
	}


	List init = new ArrayList();
	while(stmtIt.hasNext()){
	    init.add(stmtIt.next());
	}
	
	return init;
    }



    /*    private List analyzeInit(List init){
	Iterator it = init.iterator();
	while(it.hasNext()){
	    AugmentedStmt as = (AugmentedStmt)it.next();
	    Stmt s = as.get_Stmt();
	    if(!(s instanceof DefinitionStmt)){
		//there is something wrong so dont do anything fancy
		return init; 
	    }
	    else{
		//get the local being initialized
		Value left = ((DefinitionStmt)s).getLeftOp();

	    }
	}
	return init;
    }
    */
}