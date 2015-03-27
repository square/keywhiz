/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package keywhiz.auth;

/**
 * Cryptographic utility methods that are somewhat subtle.  Named in honor of Go's 'subtle'
 * package.
 */
public final class Subtles {
  private Subtles() { }

  /**
   * Compare two byte arrays in constant time, so as to protect against timing attacks. (NB:
   * "Constant time" means that the method execution time does not depend on the contents of the
   * buffer — it's not a comment about the time complexity.  (The method is obviously O(n).)
   *
   * This is interesting because normal comparisons (Arrays.equals(), eg) short-circuit as soon as a
   * difference is found. This leaks information about the underlying data, and so is inappropriate
   * for comparing secrets.  For more information, see:
   *
   * <ul>
   *   <li><a href="http://emerose.com/timing-attacks-explained">this Square Security Awareness
   *     note</a></li>
   *   <li><a href="http://codahale.com/a-lesson-in-timing-attacks/">a discussion of Java 6's
   *     MessageDigest.isEqual()</a></li>
   *   <li><a href="http://crypto.stanford.edu/~dabo/papers/ssl-timing.pdf">a paper on the
   *     practical exploitability of network-based timing attacks</a></li>
   * </ul>
   *
   * @param a first value
   * @param b second value
   * @return true iff a == b
   */
  public static boolean secureCompare(final byte[] a, final byte[] b) {
    if (a.length != b.length) {
      return false;
    }

    int match = 0;
    for (int i = 0; i < a.length; i++) {
      match = match | a[i] ^ b[i];
    }

    return (match == 0); // true if match
  }

  /**
   * Compare two char arrays in constant time, so as to protect against timing attacks. (NB:
   * "Constant time" means that the method execution time does not depend on the contents of the
   * buffer — it's not a comment about the time complexity.  (The method is obviously O(n).)
   *
   * This is interesting because normal comparisons (Arrays.equals(), eg) short-circuit as soon as a
   * difference is found. This leaks information about the underlying data, and so is inappropriate
   * for comparing secrets.  For more information, see:
   *
   * <ul>
   *   <li><a href="http://emerose.com/timing-attacks-explained">this Square Security Awareness
   *     note</a></li>
   *   <li><a href="http://codahale.com/a-lesson-in-timing-attacks/">a discussion of Java 6's
   *     MessageDigest.isEqual()</a></li>
   *   <li><a href="http://crypto.stanford.edu/~dabo/papers/ssl-timing.pdf">a paper on the
   *     practical exploitability of network-based timing attacks</a></li>
   * </ul>
   *
   * @param a first value
   * @param b second value
   * @return true iff a == b
   */
  public static boolean secureCompare(final char[] a, final char[] b) {
    if (a.length != b.length) {
      return false;
    }

    int match = 0;
    for (int i = 0; i < a.length; i++) {
      match = match | a[i] ^ b[i];
    }

    return (match == 0); // true if match
  }

  /**
   * Compare two Strings in constant time, so as to protect against timing attacks. (NB:
   * "Constant time" means that the method execution time does not depend on the contents of the
   * buffer — it's not a comment about the time complexity.  (The method is obviously O(n).)
   *
   * This is interesting because normal comparisons (Arrays.equals(), eg) short-circuit as soon as a
   * difference is found. This leaks information about the underlying data, and so is inappropriate
   * for comparing secrets.  For more information, see:
   *
   * <ul>
   *   <li><a href="http://emerose.com/timing-attacks-explained">this Square Security Awareness
   *     note</a></li>
   *   <li><a href="http://codahale.com/a-lesson-in-timing-attacks/">a discussion of Java 6's
   *     MessageDigest.isEqual()</a></li>
   *   <li><a href="http://crypto.stanford.edu/~dabo/papers/ssl-timing.pdf">a paper on the
   *     practical exploitability of network-based timing attacks</a></li>
   * </ul>
   *
   * @param a first value
   * @param b second value
   * @return true iff a == b
   */
  public static boolean secureCompare(final String a, final String b) {
    return secureCompare(a.toCharArray(), b.toCharArray());
  }
}
