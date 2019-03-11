package org.aion4j.avm.idea.inspection;

import com.intellij.codeInspection.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import org.aion4j.avm.idea.service.AvmService;
import org.aion4j.avm.idea.service.MethodDescriptor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JCLWhitelistInspection extends AbstractBaseJavaLocalInspectionTool implements CustomSuppressableInspectionTool {

    private final static Logger log = Logger.getInstance(JCLWhitelistInspection.class);

    private final static String AION4j_MAVEN_PLUGIN = "aion4j-maven-plugin";

    private final static String USERLIB_PACKAGE_PREFIX = "org.aion.avm.userlib";
    private final static String AVM_API_PACKAGE_PREFIX = "org.aion.avm.api";

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        PsiFile psiFile = holder.getFile();

        AvmService service = ServiceManager.getService(psiFile.getProject(), AvmService.class);

        if(service != null) {
            if(!service.isInitialize()) {
                service.init(psiFile.getProject());
            }
            if(service.isUnderTestSource(psiFile.getVirtualFile())) {
                return DummyJavaVisitor.CONSTANT;
            }
        } else
            return DummyJavaVisitor.CONSTANT;

        return new JavaElementVisitor() {

            Project project;

            @Override
            public void visitField(PsiField field) {

                //check if inspection is applicable
                if(project == null) {
                    project = field.getProject();
                }

                if(project == null)
                    return;
                AvmService service = ServiceManager.getService(project, AvmService.class);
                if(service == null || !service.isAvmProject())
                    return;
                //end enable inspection check

                String fqName = field.getType().getCanonicalText();

                if(log.isDebugEnabled())
                    log.debug("FQNAME : " + fqName);

                if(!isCheckedType(field.getType()))  { //TODO primitive type check. Do properly
                    return;
                }

                if(fqName.startsWith(USERLIB_PACKAGE_PREFIX) || fqName.startsWith(AVM_API_PACKAGE_PREFIX))
                    return;

                if(!service.isClassAllowed(project, fqName)) {
                    holder.registerProblem(field.getOriginalElement(),
                            String.format("%s is not allowed in a Avm smart contract project", fqName), ProblemHighlightType.GENERIC_ERROR);
                }

            }

            @Override
            public void visitMethod(PsiMethod method) {

            }

            @Override
            public void visitClass(PsiClass aClass) {
                project = aClass.getProject();
            }

            @Override
            public void visitTypeElement(PsiTypeElement type) {

            }

            @Override
            public void visitClassInitializer(PsiClassInitializer initializer) {

//                if(log.isDebugEnabled()) {
//                    System.out.println("visit in class initializer level >>>>>> " + initializer.getName());
//                }
            }

            @Override
            public void visitMethodCallExpression(PsiMethodCallExpression expression) {

                //check if inspection is applicable
                if(project == null) {
                    project = expression.getProject();
                }

                if(project == null)
                    return;
                AvmService service = ServiceManager.getService(project, AvmService.class);
                if(service == null || !service.isAvmProject())
                    return;
                //end enable inspection check;

               String className = null;
               PsiMethod psiMethod = null;

                try {

                    psiMethod = ((PsiMethodCallExpressionImpl) expression).resolveMethod();
                    ClsClassImpl psiClassElm = ((ClsClassImpl) psiMethod.getParent());

                    if (psiClassElm != null) {
                        className = psiClassElm.getQualifiedName();

                        if(log.isDebugEnabled())
                            log.debug("Class: " + className);
                    }

                    if(className.startsWith(USERLIB_PACKAGE_PREFIX) || className.startsWith(AVM_API_PACKAGE_PREFIX))
                        return;
                } catch (Exception e) {
                    if(log.isDebugEnabled()) {
                        log.debug(e);
                    }
                    return;
                }

                try {

                    if(!service.isClassAllowed(project, className)) {
                        holder.registerProblem(expression.getOriginalElement(),
                                String.format("%s is not allowed in a Avm smart contract project", className), ProblemHighlightType.GENERIC_ERROR);
                    }

                    List<MethodDescriptor> methodDescriptors = service.getAllowedMethodsForClass(project, className, psiMethod.getName());

                    boolean isAllowed = false;
                    for(MethodDescriptor methodDescriptor: methodDescriptors) {

                       PsiParameter[] jvmParameters = psiMethod.getParameterList().getParameters();//getParameters();//getParameters();

                       if(methodDescriptor.getParams().size() == 0 && jvmParameters.length == 0)  {
                           isAllowed = true;
                           break;
                       }

                       if(methodDescriptor.getParams().size() != jvmParameters.length) {
                           continue;
                       } else {

                           if(log.isDebugEnabled()) {
                               log.debug("Matching param size >>> " + jvmParameters.length);
                           }
                       }

                        for(int i = 0; i < methodDescriptor.getParams().size(); i++) {

                            String param = methodDescriptor.getParams().get(i);

                            if(log.isDebugEnabled()) {
                                log.debug("Actual params:  " + param);
                                log.debug("Method params " + jvmParameters[i].getType().getCanonicalText());
                            }

                            if(param.equals(jvmParameters[i].getType().getCanonicalText())) {
                                isAllowed = true;

                                break;
                            }
                        }

                        if(isAllowed)
                            break;
                    }

                    if(!isAllowed) {
                        holder.registerProblem(expression.getOriginalElement(),
                                String.format("%s.%s is not allowed in a Avm smart contract project", className, psiMethod.getName()), ProblemHighlightType.GENERIC_ERROR);
                    }

                } catch (Exception e) {
                    //e.printStackTrace();
                    if(log.isDebugEnabled()) {
                        log.debug(e);
                    }
                }
            }

            @Override
            public void visitLocalVariable(PsiLocalVariable variable) {

                //check if inspection is applicable
                if(project == null) {
                    project = variable.getProject();
                }

                if(project == null)
                    return;
                AvmService service = ServiceManager.getService(project, AvmService.class);
                if(service == null || !service.isAvmProject())
                    return;
                //end enable inspection check

                String fqName = variable.getType().getCanonicalText();

                if(log.isDebugEnabled())
                    log.debug("FQNAME : " + fqName);

                if(!isCheckedType(variable.getType())) { //TODO primitive type check. Do properly
                    return;
                }

                if(fqName.startsWith(USERLIB_PACKAGE_PREFIX) || fqName.startsWith(AVM_API_PACKAGE_PREFIX))
                    return;

                if(!service.isClassAllowed(project, fqName)) {
                    holder.registerProblem(variable.getOriginalElement(),
                            String.format("%s is not allowed in a Avm smart contract project", fqName), ProblemHighlightType.GENERIC_ERROR);
                }
            }
        };
    }

    private boolean isCheckedType(PsiType type) {
        if (!(type instanceof PsiClassType)) return false;
        else
            return true;
    }

    @Nullable
    public ProblemDescriptor[] checkField(@NotNull PsiField field, @NotNull InspectionManager manager, boolean isOnTheFly) {
        return null;
    }

    @Nls
    @NotNull
    @Override
    public String getGroupDisplayName() {
        return "Aion Avm";
    }

    /*
    @NotNull
    @Override
    public String getShortName() {
        return this.getClass().getName();
    }*/

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
        return "Avm JCL Whitelist check";
    }

    @Nullable
    @Override
    public SuppressIntentionAction[] getSuppressActions(@Nullable PsiElement element) {
        return new SuppressIntentionAction[0];
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

}
