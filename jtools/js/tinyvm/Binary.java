package js.tinyvm;

import java.util.Hashtable;
import java.util.Vector;

import javax.swing.ProgressMonitor;

import js.common.ToolProgressMonitor;
import js.tinyvm.io.IByteWriter;
import js.tinyvm.util.HashVector;

/**
 * Abstraction for dumped binary.
 */
public class Binary
{
   // State that is written to the binary:
   final RecordTable iEntireBinary = new RecordTable("binary", true, false);

   // Contents of binary:
   final MasterRecord iMasterRecord = new MasterRecord(this);
   final RecordTable iClassTable = new RecordTable("class table", false, false);
   final RecordTable iStaticState = new RecordTable("static state", true, true);
   final RecordTable iStaticFields = new RecordTable("static fields", true,
      false);
   RecordTable iConstantTable = new RecordTable("constants", false, false);
   RecordTable iMethodTables = new RecordTable("methods", true, false);
   RecordTable iExceptionTables = new RecordTable("exceptions", false, false);
   final RecordTable iInstanceFieldTables = new RecordTable("instance fields",
      true, false);
   final RecordTable iCodeSequences = new RecordTable("code", true, false);
   RecordTable iConstantValues = new RecordTable("constant values", true,
      false);
   final RecordTable iEntryClassIndices = new RecordTable(
      "entry class indices", true, false);

   // Other state:
   final Hashtable iSpecialSignatures = new Hashtable();
   final Hashtable iClasses = new Hashtable();
   final HashVector iSignatures = new HashVector();

   /**
    * Constructor.
    */
   public Binary ()
   {}

   /**
    * Dump.
    * 
    * @param writer
    * @throws TinyVMException
    */
   public void dump (IByteWriter writer) throws TinyVMException
   {
      iEntireBinary.dump(writer);
   }

   //
   // TODO public interface
   //

   //
   // TODO protected interface
   //

   //
   // classes
   //

   /**
    * Add a class.
    * 
    * @param className class name with '/'
    * @param classRecord
    */
   protected void addClassRecord (String className, ClassRecord classRecord)
   {
      assert className != null: "Precondition: className != null";
      assert classRecord != null: "Precondition: classRecord != null";
      assert className.indexOf('.') == -1: "Precondition: className is in correct form";

      iClasses.put(className, classRecord);
      iClassTable.add(classRecord);
   }

   /**
    * Has class in binary a public static void main (String[] args) method?
    * 
    * @param className class name with '/'
    * @return
    */
   public boolean hasMain (String className)
   {
      assert className != null: "Precondition: className != null";
      assert className.indexOf('.') == -1: "Precondition: className is in correct form";

      ClassRecord pRec = getClassRecord(className);
      return pRec.hasMethod(new Signature("main", "([Ljava/lang/String;)V"),
         true);
   }

   /**
    * Get class record with given signature.
    * 
    * @param className class name with '/'
    * @return class record or null if not found
    */
   public ClassRecord getClassRecord (String className)
   {
      assert className != null: "Precondition: className != null";
      assert className.indexOf('.') == -1: "Precondition: className is in correct form";

      return (ClassRecord) iClasses.get(className);
   }

   /**
    * Get index of class in binary by its signature.
    * 
    * @param className class name with '/'
    * @return index of class in binary or -1 if not found
    */
   public int getClassIndex (String className)
   {
      assert className != null: "Precondition: className != null";
      assert className.indexOf('.') == -1: "Precondition: className is in correct form";

      return getClassIndex(getClassRecord(className));
   }

   /**
    * Get index of class in binary by its class record.
    * 
    * @param classRecord
    * @return index of class in binary or -1 if not found
    */
   public int getClassIndex (ClassRecord classRecord)
   {
      if (classRecord == null)
      {
         return -1;
      }

      return iClassTable.indexOf(classRecord);
   }

   //
   // constants
   //

   /**
    * Get constant record with given index.
    * 
    * @param index
    * @return constant record or null if not found
    */
   public ConstantRecord getConstantRecord (int index)
   {
      assert index >= 0: "Precondition: index >= 0";

      return (ConstantRecord) iConstantTable.get(index);
   }

   /**
    * Get index of constant in binary by its constant record.
    * 
    * @param constantRecord
    * @return index of constant in binary or -1 if not found
    */
   public int getConstantIndex (ConstantRecord constantRecord)
   {
      if (constantRecord == null)
      {
         return -1;
      }

      return iConstantTable.indexOf(constantRecord);
   }

   //
   // processing
   //

   /**
    * Create closure.
    * 
    * @param entryClassNames names of entry class with '/'
    * @param classPath class path
    * @param all do not filter classes?
    */
   public static Binary createFromClosureOf (String[] entryClassNames,
      ClassPath classPath, boolean all) throws TinyVMException
   {
      Binary result = new Binary();
      // From special classes and entry class, store closure
      result.processClasses(entryClassNames, classPath);
      // Store special signatures
      result.processSpecialSignatures();
      result.processConstants();
      result.processMethods(all);
      result.processFields();
      if (!all)
      {
         // Remove unused methods.
         result.markMethods(entryClassNames);
         result.processOptimizedConstants();
         result.processOptimizedMethods();
      }
      // Copy code as is (first pass)
      result.processCode(false);
      result.storeComponents();
      result.initOffsets();
      // Post-process code after offsets are set (second pass)
      result.processCode(true);

      assert result != null: "Postconditon: result != null";
      return result;
   }

   public void processClasses (String[] entryClassNames, ClassPath classPath)
      throws TinyVMException
   {
      assert entryClassNames != null: "Precondition: entryClassNames != null";
      assert classPath != null: "Precondition: classPath != null";

      Vector pInterfaceMethods = new Vector();

      // Add special all classes first
      String[] specialClasses = SpecialClassConstants.CLASSES;
      //_logger.log(Level.INFO, "Starting with " + specialClasses.length
      //   + " special classes.");
      for (int i = 0; i < specialClasses.length; i++)
      {
         String className = specialClasses[i];
         ClassRecord classRecord = ClassRecord.getClassRecord(className,
            classPath, this);
         addClassRecord(className, classRecord);
         // classRecord.useAllMethods();
      }

      // Now add entry classes
      // _logger.log(Level.INFO, "Starting with " + entryClassNames.length
      //    + " entry classes.");
      for (int i = 0; i < entryClassNames.length; i++)
      {
         String className = entryClassNames[i];
         ClassRecord classRecord = ClassRecord.getClassRecord(className,
            classPath, this);
         addClassRecord(className, classRecord);
         classRecord.useAllMethods();
         // Update table of indices to entry classes
         iEntryClassIndices.add(new EntryClassIndex(this, className));
      }

      // Now add the closure.
      // _logger.log(Level.INFO, "Starting with " + iClassTable.size()
      //    + " classes.");
      // Yes, call iClassTable.size() in every pass of the loop.
      for (int pIndex = 0; pIndex < iClassTable.size(); pIndex++)
      {
         ClassRecord classRecord = (ClassRecord) iClassTable.get(pIndex);
         classRecord.storeReferredClasses(iClasses, iClassTable, classPath,
            pInterfaceMethods);
      }

      // Initialize indices and flags
      int pSize = iClassTable.size();
      for (int pIndex = 0; pIndex < pSize; pIndex++)
      {
         ClassRecord classRecord = (ClassRecord) iClassTable.get(pIndex);
         for (int i = 0; i < pInterfaceMethods.size(); i++)
         {
            classRecord.addUsedMethod((String) pInterfaceMethods.elementAt(i));
         }

         classRecord.iIndex = pIndex;
         classRecord.initFlags();
         classRecord.initParent();
      }
   }
   
   
   public void markMethods (String[] entryClassNames)
      throws TinyVMException
   {
      /* First stage of unused method elimination. Starting with the callable
       * root methods we need to mark all callable methods. We recursively wallk
       * the code for each method marking and walking new methods as we come
       * to them. We need to take particular care with interfaces and with
       * over-ridden methods to ensure that all possible destinations are
       * included.
       */
      
      /* For interfaces we need to ensure that for every method in an interface
       * that ends up being marked we locate all possible implementations
       * of that method. To do that we search all classes for those that
       * implement an interface and associate those classes with the interface.
       * Then when we mark a method in the interface we can mark all possible
       * implementations.
       *
       * We also need to handle marking methods that may be over-ridden by a 
       * method in a sub class. We locate all such methods and link them to the
       * "super-method" if this gets marked we also mark the sub-methods.
       */
      int pSize = iClassTable.size();
      for (int pIndex = 0; pIndex < pSize; pIndex++)
      {
         ClassRecord classRecord = (ClassRecord) iClassTable.get(pIndex);
         classRecord.addInterfaces(classRecord);
         classRecord.findHiddenMethods();
      }
      // Add the methods that can be called directly from the vm
      Signature staticInit = new Signature("<clinit>()V");
      Signature runMethod = new Signature("run()V");
      for (int pIndex = 0; pIndex < pSize; pIndex++)
      {
         ClassRecord classRecord = (ClassRecord) iClassTable.get(pIndex);
         /*
         if (classRecord.hasStaticInitializer())
         {
             MethodRecord pRec = classRecord.getMethodRecord(staticInit);
             classRecord.markMethod(pRec);
         }*/
         if (classRecord.hasMethod(runMethod, false))
         {
             MethodRecord pRec = classRecord.getMethodRecord(runMethod);
             classRecord.markMethod(pRec);             
         }
      }

      // Now add entry classes
      // _logger.log(Level.INFO, "Starting with " + entryClassNames.length
      //    + " entry classes.");
      for (int i = 0; i < entryClassNames.length; i++)
      {
         String className = entryClassNames[i];
         ClassRecord classRecord = getClassRecord(className);
         classRecord.markMethods();
      }

   }

   public void processSpecialSignatures ()
   {
      for (int i = 0; i < SpecialSignatureConstants.SIGNATURES.length; i++)
      {
         Signature pSig = new Signature(SpecialSignatureConstants.SIGNATURES[i]);
         iSignatures.addElement(pSig);
         iSpecialSignatures.put(pSig, SpecialSignatureConstants.SIGNATURES[i]);
      }
   }

   public boolean isSpecialSignature (Signature aSig)
   {
      return iSpecialSignatures.containsKey(aSig);
   }

   public void processConstants () throws TinyVMException
   {
      int pSize = iClassTable.size();
      for (int pIndex = 0; pIndex < pSize; pIndex++)
      {
         ClassRecord pRec = (ClassRecord) iClassTable.get(pIndex);
         pRec.storeConstants(iConstantTable, iConstantValues);
      }
   }
   
   public void processOptimizedConstants () throws TinyVMException
   {
      int pSize = iConstantTable.size();
      RecordTable iOptConstantTable = new RecordTable("constants", false, false);
      RecordTable iOptConstantValues = new RecordTable("constant values", true, false);
      
      for (int pIndex = 0; pIndex < pSize; pIndex++)
      {
         ConstantRecord pRec = (ConstantRecord) iConstantTable.get(pIndex);
         if (pRec.used())
         {
            iOptConstantTable.add(pRec);
            iOptConstantValues.add(pRec.constantValue());
         }
      }
      iConstantTable = iOptConstantTable;
      iConstantValues = iOptConstantValues;
   }

   /**
    * Calls storeMethods on all the classes of the closure previously computed
    * with processClasses.
    * 
    * @throws TinyVMException
    */
   public void processMethods (boolean iAll) throws TinyVMException
   {
      int pSize = iClassTable.size();
      for (int pIndex = 0; pIndex < pSize; pIndex++)
      {
         ClassRecord classRecord = (ClassRecord) iClassTable.get(pIndex);
         classRecord.storeMethods(iMethodTables, iExceptionTables, iSignatures,
            iAll);
      }
   }
   
   
   public void processOptimizedMethods () throws TinyVMException
   {
      /* This is the second stage of the unused methods elimination code.
       * We need to re-create the method and exception tables so that they
       * only contain methods that are actually called.
       */
      int pSize = iClassTable.size();
      // We need an optimized version of the method and exception tables
      // so create new ones and repopulate.
      iMethodTables = new RecordTable("methods", true, false);
      iExceptionTables = new RecordTable("exceptions", false, false);

      for (int pIndex = 0; pIndex < pSize; pIndex++)
      {
         ClassRecord classRecord = (ClassRecord) iClassTable.get(pIndex);
         classRecord.storeOptimizedMethods(iMethodTables, iExceptionTables, iSignatures);
      }
   }

   public void processFields () throws TinyVMException
   {
      int pSize = iClassTable.size();
      for (int pIndex = 0; pIndex < pSize; pIndex++)
      {
         ClassRecord pRec = (ClassRecord) iClassTable.get(pIndex);
         pRec.storeFields(iInstanceFieldTables, iStaticFields, iStaticState);
      }
   }

   public void processCode (boolean aPostProcess) throws TinyVMException
   {
      int pSize = iClassTable.size();
      for (int pIndex = 0; pIndex < pSize; pIndex++)
      {
         ClassRecord pRec = (ClassRecord) iClassTable.get(pIndex);
         pRec.storeCode(iCodeSequences, aPostProcess);
      }
   }

   //
   // storing
   //

   public void storeComponents ()
   {
      // Master record and class table are always the first two:
      iEntireBinary.add(iMasterRecord);
      iEntireBinary.add(iClassTable);
      // 5 aligned components:
      iEntireBinary.add(iStaticState);
      iEntireBinary.add(iStaticFields);
      iEntireBinary.add(iConstantTable);
      iEntireBinary.add(iMethodTables);
      iEntireBinary.add(iExceptionTables);
      // 4 unaligned components:
      iEntireBinary.add(iInstanceFieldTables);
      iEntireBinary.add(iCodeSequences);
      iEntireBinary.add(iConstantValues);
      iEntireBinary.add(iEntryClassIndices);
   }

   public void initOffsets () throws TinyVMException
   {
      iEntireBinary.initOffset(0);
   }

   public int getTotalNumMethods ()
   {
      int pTotal = 0;
      int pSize = iMethodTables.size();
      for (int i = 0; i < pSize; i++)
      {
         pTotal += ((RecordTable) iMethodTables.get(i)).size();
      }
      return pTotal;
   }

   public int getTotalNumInstanceFields ()
   {
      int pTotal = 0;
      int pSize = iInstanceFieldTables.size();
      for (int i = 0; i < pSize; i++)
      {
         pTotal += ((RecordTable) iInstanceFieldTables.get(i)).size();
      }
      return pTotal;
   }

   public int getTotalNumExceptionRecords()
   {
      int pTotal = 0;
      int pSize = iExceptionTables.size();
      for (int i = 0; i < pSize; i++)
      {
         pTotal += ((RecordTable) iExceptionTables.get(i)).size();
      }
      return pTotal;
   }
           
   // private static final Logger _logger = Logger.getLogger("TinyVM");


   public void log(ToolProgressMonitor monitor) throws TinyVMException {
	   
     // all classes
     for (int pIndex = 0; pIndex < iClassTable.size(); pIndex++)
     {
       ClassRecord pRec = (ClassRecord) iClassTable.get(pIndex);
       monitor.log("Class " + pIndex + ": " + pRec.iName);
     }
     /*
     int pSize = iSignatures.size();
     for (int i = 0; i < pSize; i++)
     {
       Signature pSig = (Signature) iSignatures.elementAt (i);
       monitor.log("Signature " + i + ": " + pSig.getImage());
     }
	 */
     int pSize = iMethodTables.size();
	 int methodNo = 0;
     for (int i = 0; i < pSize; i++)
     {
		RecordTable rt = ((RecordTable) iMethodTables.get(i));
        int cnt = rt.size();
		for(int j = 0; j < cnt; j++)
		{
			MethodRecord mr = (MethodRecord) (rt.get(j));
			monitor.log("Method " + methodNo + ": Class: " + mr.iClassRecord.getName() + " Signature: " + 
					     ((Signature)iSignatures.elementAt(mr.iSignatureId)).getImage() + " PC " + mr.getCodeStart());
			methodNo++;
			
		}
     }
     monitor.log("Master record    : " + iMasterRecord.getLength() + " bytes.");
     monitor.log("Class records    : " + iClassTable.size() + " (" + iClassTable.getLength() + " bytes).");
     monitor.log("Field records    : " + getTotalNumInstanceFields() + " (" + iInstanceFieldTables.getLength() + " bytes).");
     monitor.log("Static fields    : " + iStaticFields.size() + " (" + iStaticFields.getLength() + " bytes).");
     monitor.log("Static state     : " + iStaticState.size() + " (" + iStaticState.getLength() + " bytes).");
     monitor.log("Constant records : " + iConstantTable.size() + " (" + iConstantTable.getLength() + " bytes).");
     monitor.log("Constant values  : " + iConstantValues.size() + " (" + iConstantValues.getLength() + " bytes).");
     monitor.log("Method records   : " + getTotalNumMethods() + " (" + iMethodTables.getLength() + " bytes).");
     //monitor.log("Exception records: " + iExceptionTables.size() + " (" + iExceptionTables.getLength() + " bytes).");
     monitor.log("Exception records: " + getTotalNumExceptionRecords() + " (" + iExceptionTables.getLength() + " bytes).");
     monitor.log("Code             : " + iCodeSequences.size() + " (" + iCodeSequences.getLength() + " bytes).");
     monitor.log("Total            : " + iEntireBinary.getLength() + " bytes.");
   }
   
}

