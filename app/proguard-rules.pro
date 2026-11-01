# VTM 0.25.0 marks a few methods with javax.annotation.CheckReturnValue (JSR-305). That annotation
# is for compilers and is never loaded at run time; Mantra ARKOD gets the class through one of its
# other libraries, this app has none of them, and R8 stopped on "Missing class" (1.11.2026, found
# by the build in the cloud session before the first push). Nothing is kept: only the warning goes.
-dontwarn javax.annotation.CheckReturnValue
