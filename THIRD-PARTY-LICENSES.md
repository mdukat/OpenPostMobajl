# Third-Party Licenses

OpenPostMobajl itself — source code, build scripts, documentation, and the
launcher icon — is licensed under the MIT License. See [LICENSE.md](LICENSE.md).

This file lists the third-party components used to build or run the project,
their versions, licenses, and where to find the full license texts.

## Summary

| Component | Version | License | Used | Source |
|---|---|---|---|---|
| ZXing core | 3.5.3 | Apache-2.0 | Runtime (QR generation) | https://github.com/zxing/zxing |
| Android SDK build-tools | 34.0.0 | Android SDK License | Build time | https://developer.android.com/studio/terms |
| Android SDK platform | android-34 (platform-34-ext7_r03) | Android SDK License | Build time | https://developer.android.com/studio/terms |
| Android SDK platform-tools | 37.0.1 | Android SDK License | Build time | https://developer.android.com/studio/terms |
| OpenJDK | 17.0.21.0.3_p1-r0 | GPL-2.0-with-classpath-exception | Build time | https://github.com/openjdk/jdk17u |
| gcompat | 1.1.0-r4 | NCSA | Build time (gcompat chroot) | https://git.adelielinux.org/adelie/gcompat |
| libucontext | 1.5.1-r0 | ISC | Build time | https://github.com/kaniini/libucontext |
| musl-obstack | 1.2.3-r2 | GPL-2.0-or-later | Build time | https://github.com/void-linux/musl-obstack |
| libgcc | 15.2.0-r5 | GPL-2.0-or-later AND LGPL-2.1-or-later | Build time | https://gcc.gnu.org |
| Alpine Linux packages | v3.24 | Various (see above) | Build time | https://alpinelinux.org |
| Launcher icon (`kuki.png` derivatives) | — | MIT | Runtime (icon) | Project owner |

The Android SDK, OpenJDK, Alpine packages, and the ZXing jar are downloaded by
`bootstrap.sh`; they are not committed to this repository. The APK contains the
ZXing core classes and the project's own code and icon. The build tools are used
at build time only and are not distributed in the APK.

## Runtime components (included in the APK)

### ZXing core 3.5.3

- Project: https://github.com/zxing/zxing
- License: Apache License 2.0
- Used for: local QR code generation in `ParcelDetailActivity`
- Downloaded by `bootstrap.sh` into `libs/zxing-core-3.5.3.jar`

The ZXing jar does not ship a separate `NOTICE` file. The Apache License 2.0
text is reproduced below.

```text
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. (Don't include
      the brackets!)  The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright [yyyy] [name of copyright owner]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```

## Build-time components (downloaded by bootstrap.sh, not in the APK)

### Android SDK

- Components: build-tools 34.0.0, platform android-34
  (`platform-34-ext7_r03`), platform-tools 37.0.1
- License: Android Software Development Kit License Agreement
- Terms: https://developer.android.com/studio/terms
- The SDK packages include `NOTICE.txt` files with the notices for the
  open-source components they contain:
  - `tools/android-sdk/build-tools/34.0.0/NOTICE.txt`
  - `tools/android-sdk/platform-tools/NOTICE.txt`
- Downloaded by `bootstrap.sh` from
  `https://dl.google.com/android/repository`.

### OpenJDK 17

- Packages: `openjdk17-jdk`, `openjdk17-jre-headless`
- Version: 17.0.21.0.3_p1-r0
- License: GPL-2.0-with-classpath-exception
- Upstream: https://github.com/openjdk/jdk17u
- GPL-2.0: https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
- Classpath Exception:
  https://openjdk.org/legal/gplv2+ce.html
- Downloaded by `bootstrap.sh` from Alpine Linux.

### Alpine Linux packages

The bootstrap script downloads these Alpine packages into `./mirror/`:

| Package | Version | License | Upstream |
|---|---|---|---|
| `gcompat` | 1.1.0-r4 | NCSA | https://git.adelielinux.org/adelie/gcompat |
| `libucontext` | 1.5.1-r0 | ISC | https://github.com/kaniini/libucontext |
| `musl-obstack` | 1.2.3-r2 | GPL-2.0-or-later | https://github.com/void-linux/musl-obstack |
| `libgcc` | 15.2.0-r5 | GPL-2.0-or-later AND LGPL-2.1-or-later | https://gcc.gnu.org |
| `openjdk17-jdk` | 17.0.21.0.3_p1-r0 | GPL-2.0-with-classpath-exception | https://github.com/openjdk/jdk17u |
| `openjdk17-jre-headless` | 17.0.21.0.3_p1-r0 | GPL-2.0-with-classpath-exception | https://github.com/openjdk/jdk17u |

License texts:

- NCSA (gcompat):
  https://git.adelielinux.org/adelie/gcompat/-/blob/master/LICENSE
- ISC (libucontext):
  https://github.com/kaniini/libucontext/blob/master/LICENSE
- GPL-2.0:
  https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
- LGPL-2.1:
  https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
- GCC Runtime Library Exception:
  https://www.gnu.org/licenses/gcc-exception-3.1.html

#### NCSA (gcompat)

```text
University of Illinois/NCSA Open Source License

Copyright (c) <Year> <Owner Organization Name>. All rights reserved.

Developed by: <Name of Development Group>
              <Name of Institution>
              <URL for Development Group/Institution>

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal with
the Software without restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimers.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimers in the documentation
  and/or other materials provided with the distribution.

* Neither the names of <Name of Development Group, Name of Institution>, nor the
  names of its contributors may be used to endorse or promote products derived
  from this Software without specific prior written permission.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE CONTRIBUTORS
OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS WITH THE SOFTWARE.
```

#### ISC (libucontext)

```text
ISC License

Copyright (c) <Year> <Owner>

Permission to use, copy, modify, and/or distribute this software for any purpose
with or without fee is hereby granted, provided that the above copyright notice
and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS
OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
THIS SOFTWARE.
```

The Alpine packages are used only to build the app and are not distributed in
the APK.

## Project icon

The launcher icon is derived from `kuki.png` and is licensed under the MIT
License, copyright (c) 2026 Mateusz Dukat. The resized icon in
`app/res/drawable-nodpi/ic_launcher_image.png` is part of the project and is
covered by [LICENSE.md](LICENSE.md).

## Notes

- The Android SDK, OpenJDK, Alpine packages, and ZXing jar are downloaded by
  `bootstrap.sh`; they are not committed to this repository.
- The APK contains the ZXing core classes and the project's own code and icon.
- The build tools are used at build time only and are not distributed in the
  APK.
- The APK bundles [LICENSE.md](LICENSE.md) and this file as assets and shows
  them from the sidebar's **Licenses** button.
- If you redistribute the APK, keep both files with it and comply with the
  Apache License 2.0 for ZXing.
