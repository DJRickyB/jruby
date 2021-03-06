/***** BEGIN LICENSE BLOCK *****
 * Version: EPL 2.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby;

import java.io.IOException;
import java.math.BigInteger;
import java.math.RoundingMode;

import org.jcodings.specific.ASCIIEncoding;
import org.jcodings.specific.USASCIIEncoding;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.ast.util.ArgsUtil;
import org.jruby.common.IRubyWarnings;
import org.jruby.runtime.Arity;
import org.jruby.runtime.CallSite;
import org.jruby.runtime.ClassIndex;
import org.jruby.runtime.JavaSites;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ObjectMarshal;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.marshal.MarshalStream;
import org.jruby.runtime.marshal.UnmarshalStream;
import org.jruby.util.ByteList;
import org.jruby.util.Numeric;
import org.jruby.util.TypeConverter;

import static org.jruby.runtime.Helpers.invokedynamic;
import static org.jruby.runtime.invokedynamic.MethodNames.HASH;
import static org.jruby.util.Numeric.*;

/**
 * rational.c (as of revision: 20011)
 */
@JRubyClass(name = "Rational", parent = "Numeric")
public class RubyRational extends RubyNumeric {
    
    public static RubyClass createRationalClass(Ruby runtime) {
        RubyClass rationalc = runtime.defineClass("Rational", runtime.getNumeric(), RATIONAL_ALLOCATOR);
        runtime.setRational(rationalc);

        rationalc.setClassIndex(ClassIndex.RATIONAL);
        rationalc.setReifiedClass(RubyRational.class);
        
        rationalc.kindOf = new RubyModule.JavaClassKindOf(RubyRational.class);

        rationalc.setMarshal(RATIONAL_MARSHAL);
        rationalc.defineAnnotatedMethods(RubyRational.class);

        rationalc.getSingletonClass().undefineMethod("allocate");
        rationalc.getSingletonClass().undefineMethod("new");

        return rationalc;
    }

    private static ObjectAllocator RATIONAL_ALLOCATOR = new ObjectAllocator() {
        @Override
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            RubyFixnum zero = RubyFixnum.zero(runtime);
            return new RubyRational(runtime, klass, zero, zero);
        }
    };

    private RubyRational(Ruby runtime, RubyClass clazz, RubyInteger num, RubyInteger den) {
        super(runtime, clazz);
        this.num = num;
        this.den = den;
    }

    private RubyRational(Ruby runtime, RubyClass clazz, IRubyObject num, IRubyObject den) {
        this(runtime, clazz, num.convertToInteger(), den.convertToInteger());
    }

    /** rb_rational_raw
     * 
     */
    public static RubyRational newRationalRaw(Ruby runtime, IRubyObject x, IRubyObject y) {
        return new RubyRational(runtime, runtime.getRational(), x, y);
    }

    /** rb_rational_raw1
     * 
     */
    static RubyRational newRationalRaw(Ruby runtime, IRubyObject x) {
        return new RubyRational(runtime, runtime.getRational(), x, RubyFixnum.one(runtime));
    }

    /** rb_rational_new1
     * 
     */
    static RubyNumeric newRationalCanonicalize(ThreadContext context, RubyInteger x) {
        return (RubyNumeric) newRationalCanonicalize(context, x, RubyFixnum.one(context.runtime));
    }

    /** rb_rational_new
     * 
     */
    public static IRubyObject newRationalCanonicalize(ThreadContext context, RubyInteger x, RubyInteger y) {
        return canonicalizeInternal(context, context.runtime.getRational(), x, y);
    }

    public static IRubyObject newRationalCanonicalize(ThreadContext context, IRubyObject x, IRubyObject y) {
        return canonicalizeInternal(context, context.runtime.getRational(), (RubyInteger) x, (RubyInteger) y);
    }

    public static IRubyObject newRationalCanonicalize(ThreadContext context, long x, long y) {
        Ruby runtime = context.runtime;
        return canonicalizeInternal(context, runtime.getRational(), runtime.newFixnum(x), runtime.newFixnum(y));
    }

    static RubyNumeric newRationalNoReduce(ThreadContext context, RubyInteger x, RubyInteger y) {
        return canonicalizeInternalNoReduce(context, context.runtime.getRational(), x, y);
    }

    /** f_rational_new_no_reduce2
     * 
     */
    private static RubyNumeric newRationalNoReduce(ThreadContext context, RubyClass clazz, RubyInteger x, RubyInteger y) {
        return canonicalizeInternalNoReduce(context, clazz, x, y);
    }

    /** f_rational_new_bang2
     * 
     */
    private static RubyRational newRationalBang(ThreadContext context, RubyClass clazz, IRubyObject x, IRubyObject y) {
        assert !f_negative_p(context, y) && !(f_zero_p(context, y));
        return new RubyRational(context.runtime, clazz, x, y);
    }

    /** f_rational_new_bang1
     * 
     */
    private static RubyRational newRationalBang(ThreadContext context, RubyClass clazz, IRubyObject x) {
        return newRationalBang(context, clazz, x, RubyFixnum.one(context.runtime));
    }

    private static RubyRational newRationalBang(ThreadContext context, RubyClass clazz, long x) {
        return newRationalBang(context, clazz, RubyFixnum.newFixnum(context.runtime, x), RubyFixnum.one(context.runtime));
    }

    @Override
    public ClassIndex getNativeClassIndex() {
        return ClassIndex.RATIONAL;
    }
    
    private RubyInteger num;
    private RubyInteger den;

    /** nurat_canonicalization
     *
     */
    private static boolean canonicalization = false;
    public static void setCanonicalization(boolean canonical) {
        canonicalization = canonical;
    }

    /** nurat_int_check
     * 
     */
    private static RubyInteger intCheck(ThreadContext context, IRubyObject num) {
        if (num instanceof RubyInteger) return (RubyInteger) num;
        if (!(num instanceof RubyNumeric) || !integer_p(context).call(context, num, num).isTrue()) { // num.integer?
            throw context.runtime.newTypeError("can't convert " + num.getMetaClass().getName() + " into Rational");
        }
        return num.convertToInteger();
    }

    private static CallSite integer_p(ThreadContext context) {
        return context.sites.Numeric.integer;
    }

    /** nurat_int_value
     * 
     */
    static RubyInteger intValue(ThreadContext context, IRubyObject num) {
        RubyInteger i;
        if (( i = RubyInteger.toInteger(context, num) ) == null) {
            throw context.runtime.newTypeError("can't convert " + num.getMetaClass().getName() + " into Rational");
        }
        return i;
    }
    
    /** nurat_s_canonicalize_internal
     * 
     */
    private static RubyNumeric canonicalizeInternal(ThreadContext context, RubyClass clazz, RubyInteger num, RubyInteger den) {
        final int res = den.signum();
        if (res < 0) { // MRI: nurat_canonicalize, negation part (replacement)
            num = num.negate();
            den = den.negate();
        } else if (res == 0) {
            throw context.runtime.newZeroDivisionError();
        }

        RubyInteger gcd = f_gcd(context, num, den);
        RubyInteger _num = (RubyInteger) num.idiv(context, gcd);
        RubyInteger _den = (RubyInteger) den.idiv(context, gcd);

        if (Numeric.CANON && canonicalization && f_one_p(context, _den)) return _num;

        return new RubyRational(context.runtime, clazz, _num, _den);
    }

    /** nurat_s_canonicalize_internal_no_reduce
     * 
     */
    private static RubyNumeric canonicalizeInternalNoReduce(ThreadContext context, RubyClass clazz,
                                                            RubyInteger num, RubyInteger den) {
        // MRI: nurat_canonicalize, negation part
        if (canonicalizeShouldNegate(context, den)) {
            num = num.negate();
            den = den.negate();
        }

        if (Numeric.CANON && canonicalization && f_one_p(context, den)) return num;

        return new RubyRational(context.runtime, clazz, num, den);
    }

    // MRI: nurat_canonicalize, value check part
    private static boolean canonicalizeShouldNegate(ThreadContext context, RubyInteger den) {
        final Ruby runtime = context.runtime;
        final RubyFixnum zero = RubyFixnum.zero(runtime);
        long res = f_cmp(context, den, zero).getLongValue();
        if (res == 0) throw runtime.newZeroDivisionError();
        return res == -1;
    }
    
    /** nurat_s_new
     * 
     */
    @Deprecated
    public static IRubyObject newInstance(ThreadContext context, IRubyObject clazz, IRubyObject[]args) {
        switch (args.length) {
            case 1: return newInstance(context, (RubyClass) clazz, args[0]);
            case 2: return newInstance(context, (RubyClass) clazz, args[0], args[1]);
        }
        Arity.raiseArgumentError(context.runtime, args.length, 1, 1);
        return null;
    }

    @Deprecated // confusing parameters
    public static IRubyObject newInstance(ThreadContext context, IRubyObject clazz, IRubyObject num) {
        return newInstance(context, (RubyClass) clazz, num);
    }

    static RubyNumeric newInstance(ThreadContext context, RubyClass clazz, IRubyObject num) {
        return canonicalizeInternal(context, clazz, intValue(context, num), RubyFixnum.one(context.runtime));
    }

    @Deprecated
    public static IRubyObject newInstance(ThreadContext context, IRubyObject clazz, IRubyObject num, IRubyObject den) {
        return newInstance(context, (RubyClass) clazz, num, den);
    }

    static RubyNumeric newInstance(ThreadContext context, RubyClass clazz, IRubyObject num, IRubyObject den) {
        return canonicalizeInternal(context, clazz, intValue(context, num), intValue(context, den));
    }

    static RubyNumeric newInstance(ThreadContext context, RubyClass clazz, RubyInteger num, RubyInteger den) {
        return canonicalizeInternal(context, clazz, num, den);
    }

    public static RubyNumeric newInstance(ThreadContext context, RubyInteger num, RubyInteger den) {
        return canonicalizeInternal(context, context.runtime.getRational(), num, den);
    }

    public static RubyNumeric newInstance(ThreadContext context, RubyInteger num) {
        return canonicalizeInternal(context, context.runtime.getRational(), num, RubyFixnum.one(context.runtime));
    }

    /** rb_Rational1
     * 
     */
    public static IRubyObject newRationalConvert(ThreadContext context, IRubyObject x) {
        return newRationalConvert(context, x, RubyFixnum.one(context.runtime));
    }

    /** rb_Rational/rb_Rational2
     * 
     */
    public static IRubyObject newRationalConvert(ThreadContext context, IRubyObject x, IRubyObject y) {
        return convert(context, context.runtime.getRational(), x, y);
    }
    
    public static RubyRational newRational(Ruby runtime, long x, long y) {
        return new RubyRational(runtime, runtime.getRational(), runtime.newFixnum(x), runtime.newFixnum(y));
    }
    
    @Deprecated
    public static IRubyObject convert(ThreadContext context, IRubyObject clazz, IRubyObject[]args) {
        switch (args.length) {
        case 1: return convert(context, clazz, args[0]);        
        case 2: return convert(context, clazz, args[0], args[1]);
        }
        Arity.raiseArgumentError(context.runtime, args.length, 1, 1);
        return null;
    }

    /** nurat_s_convert
     * 
     */
    @JRubyMethod(name = "convert", meta = true, visibility = Visibility.PRIVATE)
    public static IRubyObject convert(ThreadContext context, IRubyObject recv, IRubyObject a1) {
        if (a1 == context.nil) {
            throw context.runtime.newTypeError("can't convert nil into Rational");
        }

        return convertCommon(context, (RubyClass) recv, a1, context.nil);
    }

    /** nurat_s_convert
     * 
     */
    @JRubyMethod(name = "convert", meta = true, visibility = Visibility.PRIVATE)
    public static IRubyObject convert(ThreadContext context, IRubyObject recv, IRubyObject a1, IRubyObject a2) {
        if (a1 == context.nil || a2 == context.nil) {
            throw context.runtime.newTypeError("can't convert nil into Rational");
        }
        return convertCommon(context, (RubyClass) recv, a1, a2);
    }
    
    private static RubyNumeric convertCommon(ThreadContext context, RubyClass clazz, IRubyObject a1, IRubyObject a2) {
        if (a1 instanceof RubyComplex) {
            RubyComplex a1c = (RubyComplex) a1;
            if (k_exact_p(a1c.getImage()) && f_zero_p(context, a1c.getImage())) a1 = a1c.getReal();
        }
        if (a2 instanceof RubyComplex) {
            RubyComplex a2c = (RubyComplex) a2;
            if (k_exact_p(a2c.getImage()) && f_zero_p(context, a2c.getImage())) a2 = a2c.getReal();
        }

        // NOTE: MRI (2.4) bypasses any custom Integer#to_r or Float#to_r implementation

        if (a1 instanceof RubyInteger) { // don't fallback to respond_to?(:to_r) bellow
            a1 = ((RubyInteger) a1).to_r(context);
        } else if (a1 instanceof RubyFloat) {
            a1 = ((RubyFloat) a1).to_r(context); // f_to_r
        } else if (a1 instanceof RubyString) {
            a1 = str_to_r_strict(context, (RubyString) a1);
        } else {
            if (a1 instanceof RubyObject && sites(context).respond_to_to_r.respondsTo(context, a1, a1)) {
                a1 = f_to_r(context, a1);
            }
        }

        if (a2 instanceof RubyFloat) {
            a2 = ((RubyFloat) a2).to_r(context); // f_to_r
        } else if (a2 instanceof RubyString) {
            a2 = str_to_r_strict(context, (RubyString) a2);
        }

        if (a1 instanceof RubyRational) {
            if (a2 == context.nil || (k_exact_p(a2) && f_one_p(context, a2))) return (RubyRational) a1;
        }

        if (a2 == context.nil) {
            if (!(a1 instanceof RubyNumeric && f_integer_p(context, (RubyNumeric) a1))) {
                return (RubyRational) TypeConverter.convertToType(context, a1, context.runtime.getRational(), sites(context).to_r_checked);
            }
            return newInstance(context, clazz, a1);
        } else {
            if ((a1 instanceof RubyNumeric && a2 instanceof RubyNumeric) &&
                (!f_integer_p(context, (RubyNumeric) a1) || !f_integer_p(context, (RubyNumeric) a2))) {
                return (RubyNumeric) f_div(context, a1, a2);
            }
            return newInstance(context, clazz, a1, a2);
        }
    }

    /** nurat_numerator
     * 
     */
    @JRubyMethod(name = "numerator")
    @Override
    public IRubyObject numerator(ThreadContext context) {
        return num;
    }

    /** nurat_denominator
     * 
     */
    @JRubyMethod(name = "denominator")
    @Override
    public IRubyObject denominator(ThreadContext context) {
        return den;
    }

    public RubyInteger getNumerator() {
        return num;
    }

    public RubyInteger getDenominator() {
        return den;
    }

    public RubyRational convertToRational() { return this; }

    @Override
    public IRubyObject zero_p(ThreadContext context) {
        return context.runtime.newBoolean(isZero());
    }

    @Override
    public final boolean isZero() {
        return num.isZero();
    }

    @Override
    public IRubyObject isNegative(ThreadContext context) {
        return context.runtime.newBoolean(signum() < 0);
    }

    @Override
    public IRubyObject isPositive(ThreadContext context) {
        return context.runtime.newBoolean(signum() > 0);
    }

    @Override
    public boolean isNegative() {
        return signum() < 0;
    }

    @Override
    public boolean isPositive() {
        return signum() > 0;
    }

    public final int signum() { return num.signum(); }

    /** f_imul
     * 
     */
    private static RubyInteger f_imul(ThreadContext context, long a, long b) {
        Ruby runtime = context.runtime;
        if (a == 0 || b == 0) {
            return RubyFixnum.zero(runtime);
        }
        if (a == 1) {
            return RubyFixnum.newFixnum(runtime, b);
        }
        if (b == 1) {
            return RubyFixnum.newFixnum(runtime, a);
        }

        long c = a * b;
        if (c / a != b) {
            return (RubyInteger) RubyBignum.newBignum(runtime, a).op_mul(context, b);
        }
        return RubyFixnum.newFixnum(runtime, c);
    }
    
    /** f_addsub
     * 
     */
    private static RubyNumeric f_addsub(ThreadContext context, RubyClass metaClass,
                                        RubyInteger anum, RubyInteger aden, RubyInteger bnum, RubyInteger bden,
                                        final boolean plus) {
        RubyInteger newNum, newDen, g, a, b;
        if (anum instanceof RubyFixnum && aden instanceof RubyFixnum &&
            bnum instanceof RubyFixnum && bden instanceof RubyFixnum) {
            long an = ((RubyFixnum)anum).getLongValue();
            long ad = ((RubyFixnum)aden).getLongValue();
            long bn = ((RubyFixnum)bnum).getLongValue();
            long bd = ((RubyFixnum)bden).getLongValue();
            long ig = i_gcd(ad, bd);

            g = RubyFixnum.newFixnum(context.runtime, ig);
            a = f_imul(context, an, bd / ig);
            b = f_imul(context, bn, ad / ig);
        } else {
            g = f_gcd(context, aden, bden);
            a = f_mul(context, anum, f_idiv(context, bden, g));
            b = f_mul(context, bnum, f_idiv(context, aden, g));
        }

        RubyInteger c = plus ? f_add(context, a, b) : f_sub(context, a, b);

        b = f_idiv(context, aden, g);
        g = f_gcd(context, c, g);
        newNum = f_idiv(context, c, g);
        a = f_idiv(context, bden, g);
        newDen = f_mul(context, a, b);
        
        return RubyRational.newRationalNoReduce(context, metaClass, newNum, newDen);
    }
    
    /** nurat_add */
    @JRubyMethod(name = "+")
    @Override
    public IRubyObject op_plus(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyInteger) {
            return f_addsub(context, getMetaClass(), num, den, (RubyInteger) other, RubyFixnum.one(context.runtime), true);
        }
        if (other instanceof RubyFloat) {
            return f_add(context, f_to_f(context, this), other);
        }
        if (other instanceof RubyRational) {
            return op_plus(context, (RubyRational) other);
        }
        return coerceBin(context, sites(context).op_plus, other);
    }

    public final RubyNumeric op_plus(ThreadContext context, RubyRational other) {
        return f_addsub(context, getMetaClass(), num, den, other.num, other.den, true);
    }

    @Deprecated
    public IRubyObject op_add(ThreadContext context, IRubyObject other) { return op_plus(context, other); }

    /** nurat_sub */
    @JRubyMethod(name = "-")
    public IRubyObject op_minus(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyInteger) {
            return f_addsub(context, getMetaClass(), num, den, (RubyInteger) other, RubyFixnum.one(context.runtime), false);
        }
        if (other instanceof RubyFloat) {
            return f_sub(context, f_to_f(context, this), other);
        }
        if (other instanceof RubyRational) {
            return op_minus(context, (RubyRational) other);
        }
        return coerceBin(context, sites(context).op_minus, other);
    }

    public final RubyNumeric op_minus(ThreadContext context, RubyRational other) {
        return f_addsub(context, getMetaClass(), num, den, other.num, other.den, false);
    }

    @Deprecated
    public IRubyObject op_sub(ThreadContext context, IRubyObject other) { return op_minus(context, other); }

    /** f_muldiv
     * 
     */
    private static RubyNumeric f_muldiv(ThreadContext context, RubyClass clazz,
                                        RubyInteger anum, RubyInteger aden,
                                        RubyInteger bnum, RubyInteger bden, final boolean mult) {
        if (!mult) {
            if (f_negative_p(context, bnum)) {
                anum = anum.negate();
                bnum = bnum.negate();
            }
            RubyInteger tmp = bnum; bnum = bden; bden = tmp;
        }
        
        final RubyInteger newNum, newDen;
        if (anum instanceof RubyFixnum && aden instanceof RubyFixnum &&
            bnum instanceof RubyFixnum && bden instanceof RubyFixnum) {
            long an = ((RubyFixnum)anum).getLongValue();
            long ad = ((RubyFixnum)aden).getLongValue();
            long bn = ((RubyFixnum)bnum).getLongValue();
            long bd = ((RubyFixnum)bden).getLongValue();
            long g1 = i_gcd(an, bd);
            long g2 = i_gcd(ad, bn);
            
            newNum = f_imul(context, an / g1, bn / g2);
            newDen = f_imul(context, ad / g2, bd / g1);
        } else {
            RubyInteger g1 = f_gcd(context, anum, bden);
            RubyInteger g2 = f_gcd(context, aden, bnum);
            
            newNum = f_mul(context, f_idiv(context, anum, g1), f_idiv(context, bnum, g2));
            newDen = f_mul(context, f_idiv(context, aden, g2), f_idiv(context, bden, g1));
        }

        return RubyRational.newRationalNoReduce(context, clazz, newNum, newDen);
    }

    /** nurat_mul
     * 
     */
    @JRubyMethod(name = "*")
    public IRubyObject op_mul(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyInteger) {
            return op_mul(context, (RubyInteger) other);
        }
        if (other instanceof RubyFloat) {
            return f_mul(context, f_to_f(context, this), other);
        }
        if (other instanceof RubyRational) {
            RubyRational otherRational = (RubyRational) other;
            return f_muldiv(context, getMetaClass(), num, den, otherRational.num, otherRational.den, true);
        }
        return coerceBin(context, sites(context).op_times, other);
    }

    final IRubyObject op_mul(ThreadContext context, RubyInteger other) {
        return f_muldiv(context, getMetaClass(), num, den, other, RubyFixnum.one(context.runtime), true);
    }

    /** nurat_div
     * 
     */
    @JRubyMethod(name = {"/", "quo"})
    public IRubyObject op_div(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyInteger) {
            return op_div(context, (RubyInteger) other);
        }
        if (other instanceof RubyFloat) {
            return f_to_f(context, this).callMethod(context, "/", other);
        }
        if (other instanceof RubyRational) {
            if (((RubyRational) other).isZero()) {
                throw context.runtime.newZeroDivisionError();
            }
            RubyRational otherRational = (RubyRational)other;
            return f_muldiv(context, getMetaClass(), num, den, otherRational.num, otherRational.den, false);
        }
        return coerceBin(context, sites(context).op_quo, other);
    }

    public final RubyNumeric op_div(ThreadContext context, RubyInteger other) {
        if (other.isZero()) {
            throw context.runtime.newZeroDivisionError();
        }
        return f_muldiv(context, getMetaClass(), num, den, other, RubyFixnum.one(context.runtime), false);
    }

    /** nurat_fdiv
     * 
     */
    @JRubyMethod(name = "fdiv")
    public IRubyObject op_fdiv(ThreadContext context, IRubyObject other) {
        return f_div(context, f_to_f(context, this), other);
    }

    /** nurat_expt
     * 
     */
    @JRubyMethod(name = "**")
    public IRubyObject op_expt(ThreadContext context, IRubyObject other) {
        if (k_exact_p(other) && f_zero_p(context, other)) {
            return RubyRational.newRationalBang(context, getMetaClass(), 1);
        }

        if (other instanceof RubyRational) {
            RubyRational otherRational = (RubyRational)other;
            if (otherRational.den.isOne()) other = otherRational.num;
        }

        // Deal with special cases of 0**n and 1**n
        if (k_numeric_p(other) && k_exact_p(other)) {
            if (den.isOne()) {
                if (num.isOne()) {
                    return RubyRational.newRationalBang(context, getMetaClass(), 1);
                }
                if (f_minus_one_p(context, num) && k_integer_p(other)) {
                    return RubyRational.newRationalBang(context, getMetaClass(), f_odd_p(context, other) ? -1 : 1);
                }
                if (f_zero_p(context, num)) {
                    if (f_negative_p(context, other)) throw context.runtime.newZeroDivisionError();
                    return RubyRational.newRationalBang(context, getMetaClass(), 0);
                }
            }
        }

        // General case
        if (other instanceof RubyInteger) {
            return fix_expt(context, (RubyInteger) other, ((RubyInteger) other).signum());
        }
        if (other instanceof RubyFloat || other instanceof RubyRational) {
            return f_expt(context, f_to_f(context, this), other);
        }
        return coerceBin(context, sites(context).op_exp, other);
    }

    public final IRubyObject op_expt(ThreadContext context, long other) {
        Ruby runtime = context.runtime;

        if (other == 0) {
            return RubyRational.newRationalBang(context, getMetaClass(), 1);
        }

        // Deal with special cases of 0**n and 1**n
        if (den.isOne()) {
            if (num.isOne()) {
                return RubyRational.newRationalBang(context, getMetaClass(), 1);
            }
            if (f_minus_one_p(context, num)) {
                return RubyRational.newRationalBang(context, getMetaClass(), other % 2 != 0 ? -1 : 1);
            }
            if (f_zero_p(context, num)) {
                if (other < 0) throw context.runtime.newZeroDivisionError();
                return RubyRational.newRationalBang(context, getMetaClass(), 0);
            }
        }

        // General case
        return fix_expt(context, RubyFixnum.newFixnum(runtime, other), Long.signum(other));
    }

    private RubyNumeric fix_expt(ThreadContext context, RubyInteger other, final int sign) {
        final RubyInteger tnum, tden;
        if (sign > 0) { // other > 0
            tnum = (RubyInteger) f_expt(context, num, other); // exp > 0
            tden = (RubyInteger) f_expt(context, den, other); // exp > 0
        } else if (sign < 0) { // other < 0
            RubyInteger otherNeg = other.negate();
            tnum = (RubyInteger) f_expt(context, den, otherNeg); // exp.negate > 0
            tden = (RubyInteger) f_expt(context, num, otherNeg); // exp.negate > 0
        } else { // other == 0
            tnum = tden = RubyFixnum.one(context.runtime);
        }
        return RubyRational.newInstance(context, getMetaClass(), tnum, tden);
    }

    /** nurat_cmp
     * 
     */
    @JRubyMethod(name = "<=>")
    @Override
    public IRubyObject op_cmp(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum || other instanceof RubyBignum) {
            if (den instanceof RubyFixnum && ((RubyFixnum) den).getLongValue() == 1) return f_cmp(context, num, other);
            return f_cmp(context, this, RubyRational.newRationalBang(context, getMetaClass(), other));
        }
        if (other instanceof RubyFloat) {
            return f_cmp(context, f_to_f(context, this), other);
        }
        if (other instanceof RubyRational) {
            RubyRational otherRational = (RubyRational) other;
            final RubyInteger num1, num2;
            if (num instanceof RubyFixnum && den instanceof RubyFixnum &&
                otherRational.num instanceof RubyFixnum && otherRational.den instanceof RubyFixnum) {
                num1 = f_imul(context, ((RubyFixnum)num).getLongValue(), ((RubyFixnum)otherRational.den).getLongValue());
                num2 = f_imul(context, ((RubyFixnum)otherRational.num).getLongValue(), ((RubyFixnum)den).getLongValue());
            } else {
                num1 = f_mul(context, num, otherRational.den);
                num2 = f_mul(context, otherRational.num, den);
            }
            return f_cmp(context, f_sub(context, num1, num2), RubyFixnum.zero(context.runtime));
        }
        return coerceCmp(context, sites(context).op_cmp, other);
    }

    /** nurat_equal_p
     * 
     */
    @JRubyMethod(name = "==")
    @Override
    public IRubyObject op_equal(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum || other instanceof RubyBignum) {
            return op_equal(context, (RubyInteger) other);
        }
        if (other instanceof RubyFloat) {
            return f_equal(context, f_to_f(context, this), other);
        }
        if (other instanceof RubyRational) {
            return op_equal(context, (RubyRational) other);
        }
        return f_equal(context, other, this);
    }

    public final IRubyObject op_equal(ThreadContext context, RubyInteger other) {
        if (num.isZero()) return context.runtime.newBoolean(other.isZero());
        if (!(den instanceof RubyFixnum) || den.getLongValue() != 1) return context.fals;
        return f_equal(context, num, other);
    }

    final RubyBoolean op_equal(ThreadContext context, RubyRational other) {
        if (num.isZero()) return context.runtime.newBoolean(other.num.isZero());
        return context.runtime.newBoolean(
                f_equal(context, num, other.num).isTrue() && f_equal(context, den, other.den).isTrue());
    }

    @Override // "eql?"
    public IRubyObject eql_p(ThreadContext context, IRubyObject other) {
        if (!(other instanceof RubyRational)) return context.fals;
        return op_equal(context, (RubyRational) other);
    }

    /** nurat_coerce
     * 
     */
    @JRubyMethod(name = "coerce")
    public IRubyObject op_coerce(ThreadContext context, IRubyObject other) {
        Ruby runtime = context.runtime;
        if (other instanceof RubyFixnum || other instanceof RubyBignum) {
            return runtime.newArray(RubyRational.newRationalBang(context, getMetaClass(), other), this);
        } else if (other instanceof RubyFloat) {
            return runtime.newArray(other, f_to_f(context, this));
        } else if (other instanceof RubyRational) {
            return runtime.newArray(other, this);
        } else if (other instanceof RubyComplex) {
            RubyComplex otherComplex = (RubyComplex)other;
            if (k_exact_p(otherComplex.getImage()) && f_zero_p(context, otherComplex.getImage())) {
                return runtime.newArray(RubyRational.newRationalBang(context, getMetaClass(), otherComplex.getReal()), this);
            } else {
                return runtime.newArray(other, RubyComplex.newComplexCanonicalize(context, this));
            }
        }
        throw runtime.newTypeError(other.getMetaClass() + " can't be coerced into " + getMetaClass());
    }

    @Override
    public IRubyObject idiv(ThreadContext context, IRubyObject other) {
        if (num2dbl(other) == 0.0) throw context.runtime.newZeroDivisionError();

        return f_floor(context, f_div(context, this, other));
    }

    public IRubyObject op_mod(ThreadContext context, IRubyObject other) {
        if (num2dbl(other) == 0.0) throw context.runtime.newZeroDivisionError();

        return f_sub(context, this, f_mul(context, other, f_floor(context, f_div(context, this, other))));
    }

    @Deprecated
    public IRubyObject op_mod19(ThreadContext context, IRubyObject other) {
        return op_mod(context, other);
    }

    /** nurat_divmod
     * 
     */
    @JRubyMethod(name = "divmod")
    public IRubyObject op_divmod(ThreadContext context, IRubyObject other) {
        if (num2dbl(other) == 0.0) throw context.runtime.newZeroDivisionError();

        IRubyObject val = f_floor(context, f_div(context, this, other));
        return context.runtime.newArray(val, f_sub(context, this, f_mul(context, other, val)));
    }

    @Deprecated
    public IRubyObject op_divmod19(ThreadContext context, IRubyObject other) {
        return op_divmod(context, other);
    }

    /** nurat_rem
     * 
     */
    @JRubyMethod(name = "remainder")
    public IRubyObject op_rem(ThreadContext context, IRubyObject other) {
        IRubyObject val = f_truncate(context, f_div(context, this, other));
        return f_sub(context, this, f_mul(context, other, val));
    }

    /** nurat_abs
     * 
     */
    @JRubyMethod(name = "abs")
    public IRubyObject op_abs(ThreadContext context) {
        if (!f_negative_p(context, this)) return this;
        return f_negate(context, this);
    }

    /**
     * MRI: nurat_floor_n
     */
    @JRubyMethod(name = "floor")
    public IRubyObject floor(ThreadContext context) {
        return roundCommon(context, context.nil, RoundingMode.FLOOR);
    }

    @JRubyMethod(name = "floor")
    public IRubyObject floor(ThreadContext context, IRubyObject n) {
        return roundCommon(context, n, RoundingMode.FLOOR);
    }

    // MRI: nurat_floor
    private IRubyObject mriFloor(ThreadContext context) {
        return num.idiv(context, den);
    }

    /**
     * MRI: nurat_ceil_n
     */
    @Override
    @JRubyMethod(name = "ceil")
    public IRubyObject ceil(ThreadContext context) {
        return roundCommon(context, context.nil, RoundingMode.CEILING);
    }

    @JRubyMethod(name = "ceil")
    public IRubyObject ceil(ThreadContext context, IRubyObject n) {
        return roundCommon(context, n, RoundingMode.CEILING);
    }

    // MRI: nurat_ceil
    private IRubyObject mriCeil(ThreadContext context) {
        return ((RubyInteger) ((RubyInteger) num.op_uminus(context)).idiv(context, den)).op_uminus(context);
    }

    @Override
    public RubyInteger convertToInteger() {
        return mriTruncate(getRuntime().getCurrentContext());
    }

    @JRubyMethod(name = "to_i")
    public IRubyObject to_i(ThreadContext context) {
        return mriTruncate(context); // truncate(context);
    }

    @Override
    public long getLongValue() {
        return convertToInteger().getLongValue();
    }

    @Override
    public BigInteger getBigIntegerValue() {
        return convertToInteger().getBigIntegerValue();
    }

    /**
     * MRI: nurat_truncate
     */
    @JRubyMethod(name = "truncate")
    public IRubyObject truncate(ThreadContext context) {
        return roundCommon(context, context.nil, RoundingMode.UNNECESSARY);
    }

    @JRubyMethod(name = "truncate")
    public IRubyObject truncate(ThreadContext context, IRubyObject n) {
        return roundCommon(context, n, RoundingMode.UNNECESSARY);
    }

    private RubyInteger mriTruncate(ThreadContext context) {
        if (num.isNegative()) {
            return ((RubyInteger) num.negate().idiv(context, den)).negate();
        }
        return (RubyInteger) num.idiv(context, den);
    }

    @JRubyMethod(name = "round")
    public IRubyObject round(ThreadContext context) {
        return roundCommon(context, context.nil, RoundingMode.HALF_UP);
    }

    @JRubyMethod(name = "round")
    public IRubyObject round(ThreadContext context, IRubyObject n) {

        IRubyObject opts = ArgsUtil.getOptionsArg(context.runtime, n);
        if (opts != context.nil) n = context.nil;

        RoundingMode mode = RubyNumeric.getRoundingMode(context, opts);

        return roundCommon(context, n, mode);
    }

    @JRubyMethod(name = "round")
    public IRubyObject round(ThreadContext context, IRubyObject n, IRubyObject opts) {
        Ruby runtime = context.runtime;

        opts = ArgsUtil.getOptionsArg(runtime, opts);
        if (!opts.isNil()) {
            n = context.nil;
        }

        RoundingMode mode = RubyNumeric.getRoundingMode(context, opts);

        return roundCommon(context, n, mode);
    }

    // MRI: f_round_common
    private IRubyObject roundCommon(ThreadContext context, final IRubyObject n, RoundingMode mode) {
        if (n == context.nil) {
            return doRound(context, mode);
        }

        final Ruby runtime = context.runtime;

        if (!(n instanceof RubyInteger)) {
            throw runtime.newTypeError(n, runtime.getInteger());
        }

        final int nsign = ((RubyInteger) n).signum();

        RubyNumeric b = f_expt(context, runtime.newFixnum(10), (RubyInteger) n);
        IRubyObject s;
        if (nsign >= 0) {
            s = this.op_mul(context, (RubyInteger) b);
        }
        else {
            s = this.op_mul(context, b); // (RubyRational) b
        }

        if (s instanceof RubyFloat) {
            if (nsign < 0) return RubyFixnum.zero(runtime);
            return this;
        }

        if (!(s instanceof RubyRational)) {
            s = newRationalBang(context, getMetaClass(), s);
        }

        s = ((RubyRational) s).doRound(context, mode);

        s = newRationalBang(context, getMetaClass(), (RubyInteger) s);
        s = ((RubyRational) s).op_div(context, b);

        if (s instanceof RubyRational && f_cmp(context, (RubyInteger) n, 1).getLongValue() < 0) {
            s = ((RubyRational) s).truncate(context);
        }

        return s;
    }

    private IRubyObject doRound(ThreadContext context, RoundingMode mode) {
        switch (mode) {
            case HALF_UP:
                return roundHalfUp(context);
            case HALF_EVEN:
                return roundHalfEven(context);
            case HALF_DOWN:
                return roundHalfDown(context);
            case FLOOR:
                return mriFloor(context);
            case CEILING:
                return mriCeil(context);
            case UNNECESSARY:
                return mriTruncate(context);
            default:
                throw context.runtime.newRuntimeError("BUG: invalid rounding mode: " + mode);
        }
    }

    // MRI: nurat_round_half_down
    private RubyInteger roundHalfDown(ThreadContext context) {

        RubyInteger num = this.num, den = this.den;

        final boolean neg = num.isNegative();

        if (neg) {
            num = (RubyInteger) num.op_uminus(context);
        }

        num = (RubyInteger) ((RubyInteger) num.op_mul(context, 2)).op_plus(context, den);
        num = (RubyInteger) num.op_minus(context, 1);
        den = (RubyInteger) den.op_mul(context, 2);
        num = (RubyInteger) num.idiv(context, den);

        if (neg) {
            num = (RubyInteger) num.op_uminus(context);
        }

        return num;
    }

    // MRI: nurat_round_half_even
    private RubyInteger roundHalfEven(ThreadContext context) {

        RubyInteger num = this.num, den = this.den;
        RubyArray qr;

        final boolean neg = num.isNegative();

        if (neg) {
            num = (RubyInteger) num.op_uminus(context);
        }

        num = (RubyInteger) ((RubyInteger) num.op_mul(context, 2)).op_plus(context, den);
        den = (RubyInteger) den.op_mul(context, 2);
        qr = (RubyArray) num.divmod(context, den);
        num = (RubyInteger) qr.eltOk(0);
        if (((RubyInteger) qr.eltOk(1)).isZero()) {
            num = (RubyInteger) num.op_and(context, RubyFixnum.newFixnum(context.runtime, ~1L));
        }

        if (neg) {
            num = (RubyInteger) num.op_uminus(context);
        }

        return num;
    }

    // MRI: nurat_round_half_up
    private RubyInteger roundHalfUp(ThreadContext context) {

        RubyInteger num = this.num, den = this.den;

        final boolean neg = num.isNegative();

        if (neg) {
            num = (RubyInteger) num.op_uminus(context);
        }

        num = (RubyInteger) ((RubyInteger) num.op_mul(context, 2)).op_plus(context, den);
        den = (RubyInteger) den.op_mul(context, 2);
        num = (RubyInteger) num.idiv(context, den);

        if (neg) {
            num = (RubyInteger) num.op_uminus(context);
        }

        return num;
    }

    /** nurat_to_f
     * 
     */

    @JRubyMethod(name = "to_f")
    public IRubyObject to_f(ThreadContext context) {
        return context.runtime.newFloat(getDoubleValue(context));
    }
    
    @Override
    public double getDoubleValue() {
        return getDoubleValue(getRuntime().getCurrentContext());
    }

    private static long ML = (long)(Math.log(Double.MAX_VALUE) / Math.log(2.0) - 1);

    public double getDoubleValue(ThreadContext context) {
        Ruby runtime = context.runtime;
        if (f_zero_p(context, num)) return 0;

        IRubyObject myNum = this.num;
        IRubyObject myDen = this.den;

        boolean minus = false;
        if (f_negative_p(context, myNum)) {
            myNum = f_negate(context, myNum);
            minus = true;
        }

        long nl = i_ilog2(context, myNum);
        long dl = i_ilog2(context, myDen);

        long ne = 0;
        if (nl > ML) {
            ne = nl - ML;
            myNum = f_rshift(context, myNum, RubyFixnum.newFixnum(runtime, ne));
        }

        long de = 0;
        if (dl > ML) {
            de = dl - ML;
            myDen = f_rshift(context, myDen, RubyFixnum.newFixnum(runtime, de));
        }

        long e = ne - de;

        if (e > 1023 || e < -1022) {
            runtime.getWarnings().warn(IRubyWarnings.ID.FLOAT_OUT_OF_RANGE, "out of Float range");
            return e > 0 ? Double.MAX_VALUE : 0;
        }

        double f = RubyNumeric.num2dbl(myNum) / RubyNumeric.num2dbl(myDen);

        if (minus) f = -f;

        f = ldexp(f, e);

        if (Double.isInfinite(f) || Double.isNaN(f)) {
            runtime.getWarnings().warn(IRubyWarnings.ID.FLOAT_OUT_OF_RANGE, "out of Float range");
        }

        return f;
    }

    /** nurat_to_r
     * 
     */
    @JRubyMethod(name = "to_r")
    public IRubyObject to_r(ThreadContext context) {
        return this;
    }

    /** nurat_rationalize
     *
     */
    @JRubyMethod(name = "rationalize", optional = 1)
    public IRubyObject rationalize(ThreadContext context, IRubyObject[] args) {

        IRubyObject a, b;

        if (args.length == 0) return to_r(context);

        if (f_negative_p(context, this)) {
            return f_negate(context, ((RubyRational) f_abs(context, this)).rationalize(context, args));
        }

        IRubyObject eps = f_abs(context, args[0]);
        a = f_sub(context, this, eps);
        b = f_add(context, this, eps);

        if (f_equal(context, a, b).isTrue()) return this;

        IRubyObject[] ans = nurat_rationalize_internal(context, a, b);

        return newInstance(context, this.metaClass, (RubyInteger) ans[0], (RubyInteger) ans[1]);
    }

    /** nurat_hash
     * 
     */
    @JRubyMethod(name = "hash")
    public IRubyObject hash(ThreadContext context) {
        return f_xor(context, (RubyInteger) invokedynamic(context, num, HASH), (RubyInteger) invokedynamic(context, den, HASH));
    }

    @Override
    public int hashCode() {
        return num.hashCode() ^ den.hashCode();
    }

    /** nurat_to_s
     * 
     */
    @JRubyMethod(name = "to_s")
    public RubyString to_s(ThreadContext context) {
        RubyString str = RubyString.newString(context.runtime, new ByteList(10), USASCIIEncoding.INSTANCE);
        str.append(num.to_s());
        str.cat((byte)'/');
        str.append(den.to_s());
        return str;
    }

    @Override
    public IRubyObject inspect() {
        return inspectImpl(getRuntime());
    }

    /** nurat_inspect
     * 
     */
    @JRubyMethod(name = "inspect")
    public RubyString inspect(ThreadContext context) {
        return inspectImpl(context.runtime);
    }

    private RubyString inspectImpl(Ruby runtime) {
        RubyString str = RubyString.newString(runtime, new ByteList(12), USASCIIEncoding.INSTANCE);
        str.cat((byte)'(');
        str.append(num.inspect());
        str.cat((byte)'/');
        str.append(den.inspect());
        str.cat((byte)')');
        return str;
    }

    /** nurat_marshal_dump
     * 
     */
    @JRubyMethod(name = "marshal_dump")
    public IRubyObject marshal_dump(ThreadContext context) {
        RubyArray dump = context.runtime.newArray(num, den);
        if (hasVariables()) dump.syncVariables(this);
        return dump;
    }

    /** nurat_marshal_load
     * 
     */
    @JRubyMethod(name = "marshal_load")
    public IRubyObject marshal_load(ThreadContext context, IRubyObject arg) {
        RubyArray load = arg.convertToArray();
        IRubyObject num = load.size() > 0 ? load.eltInternal(0) : context.nil;
        IRubyObject den = load.size() > 1 ? load.eltInternal(1) : context.nil;

        // MRI: nurat_canonicalize, negation part
        if (den != context.nil && canonicalizeShouldNegate(context, den.convertToInteger())) {
            num = f_negate(context, num);
            den = f_negate(context, den);
        }

        this.num = (RubyInteger) num;
        this.den = (RubyInteger) den;

        if (load.hasVariables()) syncVariables((IRubyObject)load);
        return this;
    }

    private static final ObjectMarshal RATIONAL_MARSHAL = new ObjectMarshal() {
        @Override
        public void marshalTo(Ruby runtime, Object obj, RubyClass type, MarshalStream marshalStream) {
            throw runtime.newTypeError("marshal_dump should be used instead for Rational");
        }

        @Override
        public Object unmarshalFrom(Ruby runtime, RubyClass type,
                                    UnmarshalStream unmarshalStream) throws IOException {
            ThreadContext context = runtime.getCurrentContext();

            RubyRational r = (RubyRational) RubyClass.DEFAULT_OBJECT_MARSHAL.unmarshalFrom(runtime, type, unmarshalStream);

            RubyInteger num = intCheck(context, r.removeInstanceVariable("@numerator"));
            RubyInteger den = intCheck(context, r.removeInstanceVariable("@denominator"));

            // MRI: nurat_canonicalize, negation part
            if (canonicalizeShouldNegate(context, den)) {
                num = num.negate();
                den = den.negate();
            }

            r.num = num;
            r.den = den;

            return r;
        }
    };

    static IRubyObject[] str_to_r_internal(final ThreadContext context, final RubyString str) {
        final Ruby runtime = context.runtime;
        final IRubyObject nil = context.nil;

        ByteList bytes = str.getByteList();

        if (bytes.getRealSize() == 0) return new IRubyObject[] { nil, str };

        IRubyObject m = RubyRegexp.newDummyRegexp(runtime, Numeric.RationalPatterns.rat_pat).match_m(context, str, false);
        
        if (m != nil) {
            RubyMatchData match = (RubyMatchData) m;
            IRubyObject si = match.at(1);
            RubyString nu = (RubyString) match.at(2);
            IRubyObject de = match.at(3);
            IRubyObject re = match.post_match(context);
            
            RubyArray a = nu.split(RubyRegexp.newDummyRegexp(runtime, Numeric.RationalPatterns.an_e_pat), context, false);
            RubyString ifp = (RubyString)a.eltInternal(0);
            IRubyObject exp = a.size() != 2 ? nil : a.eltInternal(1);
            
            a = ifp.split(RubyRegexp.newDummyRegexp(runtime, Numeric.RationalPatterns.a_dot_pat), context, false);
            IRubyObject ip = a.eltInternal(0);
            IRubyObject fp = a.size() != 2 ? nil : a.eltInternal(1);
            
            IRubyObject v = RubyRational.newRationalCanonicalize(context, (RubyInteger) f_to_i(context, ip));
            
            if (fp != nil) {
                bytes = fp.convertToString().getByteList();
                int count = 0;
                byte[] buf = bytes.getUnsafeBytes();
                int i = bytes.getBegin();
                int end = i + bytes.getRealSize();

                while (i < end) {
                    if (ASCIIEncoding.INSTANCE.isDigit(buf[i])) count++;
                    i++;
                }

                RubyInteger l = (RubyInteger) RubyFixnum.newFixnum(runtime, 10).op_pow(context, count);
                v = f_mul(context, v, l);
                v = f_add(context, v, f_to_i(context, fp));
                v = f_div(context, v, l);
            }

            if (si != nil) {
                ByteList siBytes = si.convertToString().getByteList();
                if (siBytes.length() > 0 && siBytes.get(0) == '-') v = f_negate(context, v); 
            }

            if (exp != nil) {
                v = f_mul(context, v, f_expt(context, RubyFixnum.newFixnum(runtime, 10), (RubyInteger) f_to_i(context, exp)));
            }

            if (de != nil) {
                v = f_div(context, v, f_to_i(context, de));
            }
            return new IRubyObject[] { v, re };
        }
        return new IRubyObject[] { nil, str };
    }
    
    private static RubyNumeric str_to_r_strict(ThreadContext context, RubyString str) {
        IRubyObject[] ary = str_to_r_internal(context, str);
        if (ary[0] == context.nil || ary[1].convertToString().getByteList().length() > 0) {
            throw context.runtime.newArgumentError("invalid value for convert(): " + str.inspect(context.runtime));
        }
        return (RubyNumeric) ary[0]; // (RubyRational)
    }

    /**
     * numeric_quo
     */
    public static IRubyObject numericQuo(ThreadContext context, IRubyObject x, IRubyObject y) {
        if (y instanceof RubyFloat) {
            return ((RubyNumeric)x).fdiv(context, y);
        }

        if (Numeric.CANON && canonicalization) {
            x = newRationalRaw(context.runtime, x);
        } else {
            x = TypeConverter.convertToType(context, x, context.runtime.getRational(), sites(context).to_r_checked);
        }
        return sites(context).op_quo.call(context, x, x, y);
    }

    @Deprecated
    public IRubyObject op_floor(ThreadContext context) {
        return floor(context);
    }

    @Deprecated
    public IRubyObject op_floor(ThreadContext context, IRubyObject n) {
        return floor(context, n);
    }

    @Deprecated
    public IRubyObject op_ceil(ThreadContext context) {
        return ceil(context);
    }

    @Deprecated
    public IRubyObject op_ceil(ThreadContext context, IRubyObject n) {
        return ceil(context, n);
    }

    @Deprecated
    public IRubyObject op_idiv19(ThreadContext context, IRubyObject other) {
        return idiv(context, other);
    }

    @Deprecated
    public IRubyObject op_idiv(ThreadContext context, IRubyObject other) {
        return idiv(context, other);
    }

    private static JavaSites.RationalSites sites(ThreadContext context) {
        return context.sites.Rational;
    }
}
