ScampUtils {
	classvar notesPlaying;
	*instrumentFromSynthDef { |synthDef, prefix, initialArgs=(List.new), target, addAction='addToHead'|
		var synthArgs;
		synthDef.add;
		synthArgs = SynthDescLib.global.at(synthDef.name).controlNames;
		prefix.isNil.if({ prefix = synthDef.name});
		notesPlaying.isNil.if({
			notesPlaying = Dictionary();
		});
		if(synthArgs.includes(\freq).or(synthArgs.includes(\pitch)).and(synthArgs.includes(\volume)).and(synthArgs.includes(\gate)), {
			// START NOTE
			OSCFunc({ arg msg, time, addr, recvPort;
				var id = msg[1], pitch = msg[2], volume = msg[3];
				SystemClock.schedAbs(time, {
					notesPlaying.put(id, Synth(synthDef.name,
						[\freq, pitch.midicps, \pitch, pitch, \volume, volume, \gate, 1] ++ initialArgs, target, addAction)); nil;
				})
			}, '/'++prefix++'/start_note');
			// END NOTE
			OSCFunc({ arg msg, time, addr, recvPort;
				var id = msg[1];
				SystemClock.schedAbs(time, {
					notesPlaying[id].set(\gate, 0); nil;
				});
			}, '/'++prefix++'/end_note');
			// CHANGE PITCH
			OSCFunc({ arg msg, time, addr, recvPort;
				var id = msg[1], pitch = msg[2];
				SystemClock.schedAbs(time, {
					notesPlaying[id].set(\freq, pitch.midicps);
					notesPlaying[id].set(\pitch, pitch); nil;
				});
			}, '/'++prefix++'/change_pitch');
			// CHANGE VOLUME
			OSCFunc({ arg msg, time, addr, recvPort;
				var id = msg[1], volume = msg[2];
				SystemClock.schedAbs(time, {
					notesPlaying[id].set(\volume, volume); nil;
				});
			}, '/'++prefix++'/change_volume');

			// CHANGE OTHER PARAMETERS
			synthArgs.do({ |argName|
				if([\freq, \volume, \gate].includes(argName).not, {
					OSCFunc({ arg msg, time, addr, recvPort;
						var id = msg[1], value = msg[2];
						SystemClock.schedAbs(time, {
							notesPlaying[id].set(argName, value); nil;
						});
					}, '/'++prefix++'/change_parameter/'++argName);
				});
			});

		}, {
			Error("SCAMP SynthDef must contain at least \freq, \volume, and \gate arguments").throw;
		});
	}

	*startSynthCompileListener { |path, responseAddress|
	    OSCFunc({ arg msg, time, addr, recvPort;
            var synthDef = msg[1].asString.interpret;
            ScampUtils.instrumentFromSynthDef(synthDef);
            {
                Server.default.sync;
                responseAddress.sendMsg("/done_compiling", 1);
            }.fork;
        }, path);
	}

	*startRecordingListener {
	    OSCFunc({ arg msg, time, addr, recvPort;
            var path = msg[1], channels = msg[2];
            Server.default.prepareForRecord(path.asString, channels);
            {
                Server.default.sync;
                Server.default.record;
            }.fork;
        }, "/recording/start");
        OSCFunc({ arg msg, time, addr, recvPort;
            Server.default.stopRecording;
        }, "/recording/stop");
	}

	*startQuitListener { |path, responseAddress|
	    OSCFunc({ arg msg, time, addr, recvPort;
            0.exit;
        }, "/quit");
	}
}
