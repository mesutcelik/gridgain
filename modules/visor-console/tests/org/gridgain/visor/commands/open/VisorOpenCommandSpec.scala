/* 
 Copyright (C) GridGain Systems. All Rights Reserved.
 
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

/*
 * ___    _________________________ ________
 * __ |  / /____  _/__  ___/__  __ \___  __ \
 * __ | / /  __  /  _____ \ _  / / /__  /_/ /
 * __ |/ /  __/ /   ____/ / / /_/ / _  _, _/
 * _____/   /___/   /____/  \____/  /_/ |_|
 *
 */

package org.gridgain.visor.commands.open

import org.gridgain.visor._

/**
 * Unit test for 'open' command.
 *
 * @author @java.author
 * @version @java.version
 */
class VisorOpenCommandSpec extends VisorRuntimeBaseSpec(3) {
    behavior of "A 'open' visor command"

    it should "properly connect using default configuration" in {
        visor open("-d", false)
        visor mlist()
        visor close()
    }

    it should "print error message when already connected" in {
        visor open("-d", false)
        visor open("-d", false)
        visor close()
    }

    it should "print error message about wrong parameter combination" in {
        visor open("-d -e", false)
    }
}
