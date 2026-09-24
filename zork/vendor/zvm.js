"use strict";
var ZVM = (() => {
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __commonJS = (cb, mod) => function __require() {
    return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
  };

  // ../../../../../tmp/tmp_ui9n_e9/ifvms/src/common/utils.js
  var require_utils = __commonJS({
    "../../../../../tmp/tmp_ui9n_e9/ifvms/src/common/utils.js"(exports, module) {
      "use strict";
      function extend() {
        var old = arguments[0], i = 1, add, name;
        while (i < arguments.length) {
          add = arguments[i++];
          for (name in add) {
            old[name] = add[name];
          }
        }
        return old;
      }
      function Class() {
      }
      Class.subClass = function(props) {
        function newClass() {
          if (this.init) {
            this.init.apply(this, arguments);
          }
        }
        newClass.prototype = extend(Object.create(this.prototype), props);
        newClass.subClass = this.subClass;
        newClass.super = newClass.prototype.super = this.prototype;
        return newClass;
      };
      function MemoryView(buffer, byteOffset, byteLength) {
        if (typeof buffer === "number") {
          buffer = new ArrayBuffer(buffer);
        } else if (buffer.buffer) {
          byteOffset |= 0;
          if (typeof byteLength === "undefined") {
            byteLength = buffer.byteLength - byteOffset;
          }
          byteOffset += buffer.byteOffset;
          buffer = buffer.buffer;
        }
        return extend(new DataView(buffer, byteOffset, byteLength), {
          getUint8Array: function(start, length) {
            start += this.byteOffset;
            return new Uint8Array(this.buffer.slice(start, start + length));
          },
          getUint16Array: function(start, length) {
            start += this.byteOffset;
            return Uint8toUint16Array(new Uint8Array(this.buffer, start, length * 2));
          },
          setUint8Array: function(start, data) {
            if (data instanceof ArrayBuffer) {
              data = new Uint8Array(data);
            }
            new Uint8Array(this.buffer, this.byteOffset, this.byteLength).set(data, start);
          },
          //setBuffer16 NOTE: if we implement this we cannot simply set a Uint16Array as most systems are little-endian
          // For use with IFF files
          getFourCC: function(index) {
            return String.fromCharCode(this.getUint8(index), this.getUint8(index + 1), this.getUint8(index + 2), this.getUint8(index + 3));
          },
          setFourCC: function(index, text) {
            this.setUint8(index, text.charCodeAt(0));
            this.setUint8(index + 1, text.charCodeAt(1));
            this.setUint8(index + 2, text.charCodeAt(2));
            this.setUint8(index + 3, text.charCodeAt(3));
          }
        });
      }
      function U2S16(value) {
        return value << 16 >> 16;
      }
      function S2U16(value) {
        return value & 65535;
      }
      function Uint8toUint16Array(array) {
        var i = 0, l = array.length, result = new Uint16Array(l / 2);
        while (i < l) {
          result[i / 2] = array[i++] << 8 | array[i++];
        }
        return result;
      }
      module.exports = {
        extend,
        Class,
        MemoryView,
        U2S16,
        S2U16,
        Uint8toUint16Array
      };
    }
  });

  // ../../../../../tmp/tmp_ui9n_e9/ifvms/src/common/file.js
  var require_file = __commonJS({
    "../../../../../tmp/tmp_ui9n_e9/ifvms/src/common/file.js"(exports, module) {
      "use strict";
      var utils = require_utils();
      var MemoryView = utils.MemoryView;
      var IFF = utils.Class.subClass({
        init: function(data) {
          this.type = "";
          this.chunks = [];
          if (data) {
            var view = MemoryView(data), i = 12, length, chunk_length;
            if (view.getFourCC(0) !== "FORM") {
              throw new Error("Not an IFF file");
            }
            this.type = view.getFourCC(8);
            length = view.getUint32(4) + 8;
            while (i < length) {
              chunk_length = view.getUint32(i + 4);
              if (chunk_length < 0 || chunk_length + i > length) {
                throw new Error("IFF chunk out of range");
              }
              this.chunks.push({
                type: view.getFourCC(i),
                offset: i,
                data: view.getUint8Array(i + 8, chunk_length)
              });
              i += 8 + chunk_length;
              if (chunk_length % 2) {
                i++;
              }
            }
          }
        },
        write: function() {
          var buffer_len = 12, i = 0, index = 12, out, chunk;
          while (i < this.chunks.length) {
            if (this.chunks[i].data.buffer) {
              this.chunks[i].data = this.chunks[i].data.buffer;
            }
            this.chunks[i].length = this.chunks[i].data.byteLength || this.chunks[i].data.length;
            buffer_len += 8 + this.chunks[i++].length;
            if (buffer_len % 2) {
              buffer_len++;
            }
          }
          out = MemoryView(buffer_len);
          out.setFourCC(0, "FORM");
          out.setUint32(4, buffer_len - 8);
          out.setFourCC(8, this.type);
          i = 0;
          while (i < this.chunks.length) {
            chunk = this.chunks[i++];
            out.setFourCC(index, chunk.type);
            out.setUint32(index + 4, chunk.length);
            out.setUint8Array(index + 8, chunk.data);
            index += 8 + chunk.length;
            if (index % 2) {
              index++;
            }
          }
          return out.buffer;
        }
      });
      var Blorb = IFF.subClass({
        init: function(data) {
          this.super.init.call(this, data);
          if (data) {
            if (this.type !== "IFRS") {
              throw new Error("Not a Blorb file");
            }
            if (this.chunks[0].type !== "RIdx") {
              throw new Error("Malformed Blorb: chunk 1 is not RIdx");
            }
            var view = MemoryView(this.chunks[0].data), i = 4;
            while (i < this.chunks[0].data.length) {
              if (view.getFourCC(i) === "Exec" && view.getUint32(i + 4) === 0) {
                this.exec = this.chunks.filter(function(chunk) {
                  return chunk.offset === view.getUint32(i + 8);
                })[0];
                return;
              }
              i += 12;
            }
          }
        }
      });
      var Quetzal = IFF.subClass({
        // Parse a Quetzal savefile, or make a blank one
        init: function(data) {
          this.super.init.call(this, data);
          if (data) {
            if (this.type !== "IFZS") {
              throw new Error("Not a Quetzal savefile");
            }
            var i = 0, type, chunk_data, view;
            while (i < this.chunks.length) {
              type = this.chunks[i].type;
              chunk_data = this.chunks[i++].data;
              if (type === "CMem" || type === "UMem") {
                this.memory = chunk_data;
                this.compressed = type === "CMem";
              } else if (type === "Stks") {
                this.stacks = chunk_data;
              } else if (type === "IFhd") {
                view = MemoryView(chunk_data.buffer);
                this.release = view.getUint16(0);
                this.serial = view.getUint8Array(2, 6);
                this.checksum = view.getUint16(8);
                this.pc = view.getUint32(9) & 16777215;
              }
            }
          }
        },
        // Write out a savefile
        write: function() {
          this.type = "IFZS";
          var ifhd = MemoryView(13);
          ifhd.setUint16(0, this.release);
          ifhd.setUint8Array(2, this.serial);
          ifhd.setUint32(9, this.pc);
          ifhd.setUint16(8, this.checksum);
          this.chunks = [
            { type: "IFhd", data: ifhd },
            { type: this.compressed ? "CMem" : "UMem", data: this.memory },
            { type: "Stks", data: this.stacks }
          ];
          return this.super.write.call(this);
        }
      });
      function identify(buffer) {
        var view = MemoryView(buffer), blorb, format, version;
        if (view.getFourCC(0) === "FORM" && view.getFourCC(8) === "IFRS") {
          blorb = new Blorb(buffer);
          if (blorb.exec) {
            format = blorb.exec.type;
            buffer = blorb.exec.data;
            if (format === "GLUL") {
              view = MemoryView(buffer);
              version = view.getUint32(4);
            }
            if (format === "ZCOD") {
              version = buffer[0];
            }
          }
        } else if (view.getFourCC(0) === "Glul") {
          format = "GLUL";
          version = view.getUint32(4);
        } else {
          version = view.getUint8(0);
          if (version > 0 && version < 9) {
            format = "ZCOD";
          }
        }
        if (format && version) {
          return {
            format,
            version,
            data: buffer
          };
        }
      }
      module.exports = {
        IFF,
        Blorb,
        Quetzal,
        identify
      };
    }
  });

  // ../../../../../tmp/tmp_ui9n_e9/ifvms/src/common/ast.js
  var require_ast = __commonJS({
    "../../../../../tmp/tmp_ui9n_e9/ifvms/src/common/ast.js"(exports, module) {
      "use strict";
      var utils = require_utils();
      var Class = utils.Class;
      var U2S = utils.U2S16;
      var Operand = Class.subClass({
        init: function(engine, value) {
          this.e = engine;
          this.v = value;
        },
        toString: function() {
          return this.v;
        },
        // Convert an Operand into a signed operand
        U2S: function() {
          return U2S(this.v);
        }
      });
      var Variable = Operand.subClass({
        // Get a value
        toString: function() {
          var variable = this.v;
          if (this.indirect) {
            return "e.indirect(" + variable + ")";
          }
          if (variable === 0) {
            return "s[--e.sp]";
          }
          if (--variable < 15) {
            return "l[" + variable + "]";
          }
          return "e.m.getUint16(" + (this.e.globals + (variable - 15) * 2) + ")";
        },
        // Store a value
        store: function(value) {
          var variable = this.v;
          if (this.indirect) {
            return "e.indirect(" + variable + "," + value + ")";
          }
          if (this.returnval) {
            return "e.variable(" + variable + "," + value + ")";
          }
          if (variable === 0) {
            return "t=" + value + ";s[e.sp++]=t";
          }
          if (--variable < 15) {
            return "l[" + variable + "]=" + value;
          }
          return "e.ram.setUint16(" + (this.e.globals + (variable - 15) * 2) + "," + value + ")";
        },
        // Convert an Operand into a signed operand
        U2S: function() {
          return "e.U2S(" + this + ")";
        }
      });
      var Opcode = Class.subClass({
        init: function(engine, context, code, pc, next, operands) {
          this.e = engine;
          this.context = context;
          this.code = code;
          this.pc = pc;
          this.labels = [this.pc + "/" + this.code];
          this.next = next;
          this.operands = operands;
          if (this.post) {
            this.post();
          }
        },
        // Write out the opcode, passing .operands to .func(), with a JS comment of the pc/opcode
        toString: function() {
          return this.label() + (this.func ? this.func.apply(this, this.operands) : "");
        },
        // Return a string of the operands separated by commas
        args: function(joiner) {
          return this.operands.join(joiner);
        },
        // Generate a comment of the pc and code, possibly for more than one opcode
        label: function() {
          return "/* " + this.labels.join() + " */ ";
        }
      });
      var Stopper = Opcode.subClass({
        stopper: 1
      });
      var Pauser = Stopper.subClass({
        post: function() {
          this.origfunc = this.func;
          this.func = this.newfunc;
        },
        newfunc: function() {
          return "e.stop=1;e.pc=" + this.next + ";" + this.origfunc.apply(this, arguments);
        }
      });
      var PauserStorer = Pauser.subClass({
        storer: 1,
        post: function() {
          this.storer = this.operands.pop();
          this.origfunc = this.func;
          this.func = this.newfunc;
        }
      });
      var BrancherLogic = Class.subClass({
        init: function(ops, code) {
          this.ops = ops || [];
          this.code = code || "||";
        },
        toString: function() {
          var i = 0, ops = [], op;
          while (i < this.ops.length) {
            op = this.ops[i++];
            ops.push(
              op.func ? (op.iftrue ? "" : "!(") + op.func.apply(op, op.operands) + (op.iftrue ? "" : ")") : op
            );
          }
          return (this.invert ? "(!(" : "(") + ops.join(this.code) + (this.invert ? "))" : ")");
        }
      });
      var Brancher = Opcode.subClass({
        // Flag for the disassembler
        brancher: 1,
        keyword: "if",
        // Process the branch result now
        post: function() {
          var result, prev, brancher = this.operands.pop(), offset = brancher[1];
          this.iftrue = brancher[0];
          if (offset === 0 || offset === 1) {
            result = "e.ret(" + offset + ")";
          } else {
            offset += this.next - 2;
            this.context.targets.push(offset);
            result = "e.pc=" + offset;
          }
          this.result = result + ";return";
          this.offset = offset;
          this.cond = new BrancherLogic([this]);
          if (this.context.ops.length) {
            prev = this.context.ops.pop();
            if (
              /* prev instanceof Brancher && */
              prev.offset === offset
            ) {
              this.cond.ops.unshift(prev.cond);
              this.labels = prev.labels;
              this.labels.push(this.pc + "/" + this.code);
            } else {
              this.context.ops.push(prev);
            }
          }
        },
        // Write out the brancher
        toString: function() {
          var result = this.result;
          if (result instanceof Context) {
            if (this.e.options.debug) {
              result.context = this.context;
            }
            result = result + (result.stopper ? "; return" : "");
            if (this.result.ops.length > 1) {
              result = "\n" + result + "\n";
              if (this.e.options.debug) {
                result += this.context.spacer;
              }
            }
          }
          return this.label() + this.keyword + this.cond + " {" + result + "}";
        }
      });
      var BrancherStorer = Brancher.subClass({
        storer: 1,
        // Set aside the storer operand
        post: function() {
          BrancherStorer.super.post.call(this);
          this.storer = this.operands.pop();
          this.storer.returnval = 1;
          this.origfunc = this.func;
          this.func = this.newfunc;
        },
        newfunc: function() {
          return this.storer.store(this.origfunc.apply(this, arguments));
        }
      });
      var Storer = Opcode.subClass({
        // Flag for the disassembler
        storer: 1,
        // Set aside the storer operand
        post: function() {
          this.storer = this.operands.pop();
        },
        // Write out the opcode, passing it to the storer (if there still is one)
        toString: function() {
          var data = Storer.super.toString.call(this);
          return this.storer ? this.storer.store(data) : data;
        }
      });
      var Caller = Stopper.subClass({
        // Fake a result variable
        result: { v: -1 },
        // Write out the opcode
        toString: function() {
          return this.label() + "e.call(" + this.operands.shift() + "," + this.result.v + "," + this.next + ",[" + this.args() + "])";
        }
      });
      var CallerStorer = Caller.subClass({
        // Flag for the disassembler
        storer: 1,
        post: function() {
          this.result = this.operands.pop();
        }
      });
      var Context = Class.subClass({
        init: function(engine, pc) {
          this.e = engine;
          this.pc = pc;
          this.pre = [];
          this.ops = [];
          this.post = [];
          this.targets = [];
          if (engine.options.debug) {
            this.spacer = "";
          }
        },
        toString: function() {
          if (this.e.options.debug) {
            if (this.context) {
              this.spacer = this.context.spacer + "  ";
            }
            return this.pre.join("") + (this.ops.length > 1 ? this.spacer : "") + this.ops.join(";\n" + this.spacer) + this.post.join("");
          } else {
            return this.pre.join("") + this.ops.join(";") + this.post.join("");
          }
        }
      });
      var RoutineContext = Context.subClass({
        toString: function() {
          this.pre.unshift("var l=e.l,s=e.s,t=0;\n");
          return RoutineContext.super.toString.call(this);
        }
      });
      function opcode_builder(Class2, func, flags) {
        flags = flags || {};
        if (func) {
          flags.func = func;
        }
        return Class2.subClass(flags);
      }
      module.exports = {
        Operand,
        Variable,
        Opcode,
        Stopper,
        Pauser,
        PauserStorer,
        BrancherLogic,
        Brancher,
        BrancherStorer,
        Storer,
        Caller,
        CallerStorer,
        Context,
        RoutineContext,
        opcode_builder
      };
    }
  });

  // ../../../../../tmp/tmp_ui9n_e9/ifvms/src/zvm/opcodes.js
  var require_opcodes = __commonJS({
    "../../../../../tmp/tmp_ui9n_e9/ifvms/src/zvm/opcodes.js"(exports, module) {
      "use strict";
      var AST = require_ast();
      var Variable = AST.Variable;
      var Opcode = AST.Opcode;
      var Stopper = AST.Stopper;
      var Pauser = AST.Pauser;
      var PauserStorer = AST.PauserStorer;
      var Brancher = AST.Brancher;
      var BrancherStorer = AST.BrancherStorer;
      var Storer = AST.Storer;
      var Caller = AST.Caller;
      var CallerStorer = AST.CallerStorer;
      var opcode_builder = AST.opcode_builder;
      var simple_func = function(a) {
        return "" + a;
      };
      var stack_var = new Variable(exports.e, 0);
      var alwaysbranch = opcode_builder(Brancher, function() {
        return 1;
      });
      var not = opcode_builder(Storer, function(a) {
        return "e.S2U(~" + a + ")";
      });
      var Indirect = Storer.subClass({
        storer: 0,
        post: function() {
          var operands = this.operands, op0 = operands[0], op0isVar = op0 instanceof Variable;
          operands[0] = new Variable(this.e, op0isVar ? op0 : op0.v);
          if (op0isVar || op0.v === 0) {
            operands[0].indirect = 1;
          }
          this.storer = this.code === 142 ? operands.pop() : operands.shift();
          if (operands.length === 0) {
            operands.push(stack_var);
          }
        },
        func: simple_func
      });
      var Incdec = Opcode.subClass({
        func: function(variable) {
          var varnum = variable.v - 1, operator = this.code % 2 ? 1 : -1;
          if (variable instanceof Variable || varnum > 14) {
            return "e.incdec(" + variable + "," + operator + ")";
          }
          return (varnum < 0 ? "e.s[e.sp-1]" : "e.l[" + varnum + "]") + (operator === 1 ? "++" : "--");
        }
      });
      var V3SaveRestore = Stopper.subClass({
        brancher: 1,
        toString: function() {
          return "e.stop=1;e." + (this.code === 181 ? "save" : "restore") + "(" + (this.pc + 1) + ")";
        }
      });
      var V45Restore = opcode_builder(PauserStorer, function() {
        return "e.restore(" + (this.next - 1) + ")";
      });
      var V45Save = opcode_builder(PauserStorer, function() {
        return "e.save(" + (this.next - 1) + ")";
      });
      module.exports = function(version) {
        return {
          /* je */
          1: opcode_builder(Brancher, function() {
            return arguments.length === 2 ? this.args("===") : "e.jeq(" + this.args() + ")";
          }),
          /* jl */
          2: opcode_builder(Brancher, function(a, b) {
            return a.U2S() + "<" + b.U2S();
          }),
          /* jg */
          3: opcode_builder(Brancher, function(a, b) {
            return a.U2S() + ">" + b.U2S();
          }),
          // Too many U2S/S2U for these...
          /* dec_chk */
          4: opcode_builder(Brancher, function(variable, value) {
            return "e.U2S(e.incdec(" + variable + ",-1))<" + value.U2S();
          }),
          /* inc_chk */
          5: opcode_builder(Brancher, function(variable, value) {
            return "e.U2S(e.incdec(" + variable + ",1))>" + value.U2S();
          }),
          /* jin */
          6: opcode_builder(Brancher, function() {
            return "e.jin(" + this.args() + ")";
          }),
          /* test */
          7: opcode_builder(Brancher, function() {
            return "e.test(" + this.args() + ")";
          }),
          /* or */
          8: opcode_builder(Storer, function() {
            return this.args("|");
          }),
          /* and */
          9: opcode_builder(Storer, function() {
            return this.args("&");
          }),
          /* test_attr */
          10: opcode_builder(Brancher, function() {
            return "e.test_attr(" + this.args() + ")";
          }),
          /* set_attr */
          11: opcode_builder(Opcode, function() {
            return "e.set_attr(" + this.args() + ")";
          }),
          /* clear_attr */
          12: opcode_builder(Opcode, function() {
            return "e.clear_attr(" + this.args() + ")";
          }),
          /* store */
          13: Indirect,
          /* insert_obj */
          14: opcode_builder(Opcode, function() {
            return "e.insert_obj(" + this.args() + ")";
          }),
          /* loadw */
          15: opcode_builder(Storer, function(array, index) {
            return "e.m.getUint16(e.S2U(" + array + "+2*" + index.U2S() + "))";
          }),
          /* loadb */
          16: opcode_builder(Storer, function(array, index) {
            return "e.m.getUint8(e.S2U(" + array + "+" + index.U2S() + "))";
          }),
          /* get_prop */
          17: opcode_builder(Storer, function() {
            return "e.get_prop(" + this.args() + ")";
          }),
          /* get_prop_addr */
          18: opcode_builder(Storer, function() {
            return "e.find_prop(" + this.args() + ")";
          }),
          /* get_next_prop */
          19: opcode_builder(Storer, function() {
            return "e.find_prop(" + this.args(",0,") + ")";
          }),
          /* add */
          20: opcode_builder(Storer, function() {
            return "e.S2U(" + this.args("+") + ")";
          }),
          /* sub */
          21: opcode_builder(Storer, function() {
            return "e.S2U(" + this.args("-") + ")";
          }),
          /* mul */
          22: opcode_builder(Storer, function() {
            return "e.S2U(" + this.args("*") + ")";
          }),
          /* div */
          23: opcode_builder(Storer, function(a, b) {
            return "e.S2U(parseInt(" + a.U2S() + "/" + b.U2S() + "))";
          }),
          /* mod */
          24: opcode_builder(Storer, function(a, b) {
            return "e.S2U(" + a.U2S() + "%" + b.U2S() + ")";
          }),
          /* call_2s */
          25: CallerStorer,
          /* call_2n */
          26: Caller,
          /* set_colour */
          27: opcode_builder(Opcode, function() {
            return "e.set_colour(" + this.args() + ")";
          }),
          /* throw */
          28: opcode_builder(Stopper, function(value, cookie) {
            return "while(e.frames.length+1>" + cookie + "){e.frameptr=e.frames.pop()}return " + value;
          }),
          /* jz */
          128: opcode_builder(Brancher, function(a) {
            return a + "===0";
          }),
          /* get_sibling */
          129: opcode_builder(BrancherStorer, function(obj) {
            return "e.get_sibling(" + obj + ")";
          }),
          /* get_child */
          130: opcode_builder(BrancherStorer, function(obj) {
            return "e.get_child(" + obj + ")";
          }),
          /* get_parent */
          131: opcode_builder(Storer, function(obj) {
            return "e.get_parent(" + obj + ")";
          }),
          /* get_prop_length */
          132: opcode_builder(Storer, function(a) {
            return "e.get_prop_len(" + a + ")";
          }),
          /* inc */
          133: Incdec,
          /* dec */
          134: Incdec,
          /* print_addr */
          135: opcode_builder(Opcode, function(addr) {
            return "e.print(2," + addr + ")";
          }),
          /* call_1s */
          136: CallerStorer,
          /* remove_obj */
          137: opcode_builder(Opcode, function(obj) {
            return "e.remove_obj(" + obj + ")";
          }),
          /* print_obj */
          138: opcode_builder(Opcode, function(obj) {
            return "e.print(3," + obj + ")";
          }),
          /* ret */
          139: opcode_builder(Stopper, function(a) {
            return "return " + a;
          }),
          /* jump */
          140: opcode_builder(Stopper, function(a) {
            return "e.pc=" + a.U2S() + "+" + (this.next - 2);
          }),
          /* print_paddr */
          141: opcode_builder(Opcode, function(addr) {
            return "e.print(2," + addr + "*" + this.e.addr_multipler + ")";
          }),
          /* load */
          142: Indirect.subClass({ storer: 1 }),
          143: version < 5 ? (
            /* not (v3/4) */
            not
          ) : (
            /* call_1n (v5/8) */
            Caller
          ),
          /* rtrue */
          176: opcode_builder(Stopper, function() {
            return "return 1";
          }),
          /* rfalse */
          177: opcode_builder(Stopper, function() {
            return "return 0";
          }),
          // Reconsider a generalised class for @print/@print_ret?
          /* print */
          178: opcode_builder(Opcode, function(text) {
            return "e.print(2," + text + ")";
          }, { printer: 1 }),
          /* print_ret */
          179: opcode_builder(Stopper, function(text) {
            return "e.print(2," + text + ");e.print(1,13);return 1";
          }, { printer: 1 }),
          /* nop */
          180: Opcode,
          /* save (v3/4) */
          181: version < 4 ? V3SaveRestore : V45Save,
          /* restore(v3/4) */
          182: version < 4 ? V3SaveRestore : V45Restore,
          /* restart */
          183: opcode_builder(Stopper, function() {
            return "e.restart()";
          }),
          /* ret_popped */
          184: opcode_builder(Stopper, function(a) {
            return "return " + a;
          }, { post: function() {
            this.operands.push(stack_var);
          } }),
          185: version < 5 ? (
            /* pop (v3/4) */
            opcode_builder(Opcode, function() {
              return "s[--e.sp]";
            })
          ) : (
            /* catch (v5/8) */
            opcode_builder(Storer, function() {
              return "e.frames.length+1";
            })
          ),
          /* quit */
          186: opcode_builder(Pauser, function() {
            return "e.quit=1;e.Glk.glk_exit()";
          }),
          /* new_line */
          187: opcode_builder(Opcode, function() {
            return "e.print(1,13)";
          }),
          188: version < 4 ? (
            /* show_status (v3) */
            opcode_builder(Stopper, function() {
              return "e.pc=" + this.next + ";e.v3_status()";
            })
          ) : (
            /* act as a nop in later versions */
            Opcode
          ),
          /* verify */
          189: alwaysbranch,
          // Actually check??
          /* piracy */
          191: alwaysbranch,
          /* call_vs */
          224: CallerStorer,
          /* storew */
          225: opcode_builder(Opcode, function(array, index, value) {
            return "e.ram.setUint16(e.S2U(" + array + "+2*" + index.U2S() + ")," + value + ")";
          }),
          /* storeb */
          226: opcode_builder(Opcode, function(array, index, value) {
            return "e.ram.setUint8(e.S2U(" + array + "+" + index.U2S() + ")," + value + ")";
          }),
          /* put_prop */
          227: opcode_builder(Opcode, function() {
            return "e.put_prop(" + this.args() + ")";
          }),
          /* read */
          228: version < 5 ? opcode_builder(Pauser, function() {
            return "e.read(0," + this.args() + ")";
          }) : opcode_builder(PauserStorer, function() {
            return "e.read(" + this.storer.v + "," + this.args() + ")";
          }),
          /* print_char */
          229: opcode_builder(Opcode, function(a) {
            return "e.print(4," + a + ")";
          }),
          /* print_num */
          230: opcode_builder(Opcode, function(a) {
            return "e.print(0," + a.U2S() + ")";
          }),
          /* random */
          231: opcode_builder(Storer, function(a) {
            return "e.random(" + a.U2S() + ")";
          }),
          /* push */
          232: opcode_builder(Storer, simple_func, { post: function() {
            this.storer = stack_var;
          }, storer: 0 }),
          /* pull */
          233: Indirect,
          /* split_window */
          234: opcode_builder(Opcode, function(lines) {
            return "e.split_window(" + lines + ")";
          }),
          /* set_window */
          235: opcode_builder(Opcode, function(wind) {
            return "e.set_window(" + wind + ")";
          }),
          /* call_vs2 */
          236: CallerStorer,
          /* erase_window */
          237: opcode_builder(Opcode, function(win) {
            return "e.erase_window(" + win.U2S() + ")";
          }),
          /* erase_line */
          238: opcode_builder(Opcode, function(a) {
            return "e.erase_line(" + a + ")";
          }),
          /* set_cursor */
          239: opcode_builder(Opcode, function(row, col) {
            return "e.set_cursor(" + row + "-1," + col + "-1)";
          }),
          /* get_cursor */
          240: opcode_builder(Opcode, function(addr) {
            return "e.get_cursor(" + addr + ")";
          }),
          /* set_text_style */
          241: opcode_builder(Opcode, function(stylebyte) {
            return "e.set_style(" + stylebyte + ")";
          }),
          /* buffer_mode */
          242: Opcode,
          // We don't support non-buffered output
          /* output_stream */
          243: opcode_builder(Stopper, function() {
            return "e.pc=" + this.next + ";e.output_stream(" + this.args() + ")";
          }),
          /* input_stream */
          244: opcode_builder(Pauser, function() {
            return "e.input_stream(" + this.args() + ")";
          }),
          /* sound_effect */
          245: Opcode,
          // We don't support sounds
          /* read_char */
          246: opcode_builder(PauserStorer, function() {
            return "e.read_char(" + this.storer.v + "," + (this.args() || "1") + ")";
          }),
          /* scan_table */
          247: opcode_builder(BrancherStorer, function() {
            return "e.scan_table(" + this.args() + ")";
          }),
          /* not (v5/8) */
          248: not,
          /* call_vn */
          249: Caller,
          /* call_vn2 */
          250: Caller,
          /* tokenise */
          251: opcode_builder(Opcode, function() {
            return "e.tokenise(" + this.args() + ")";
          }),
          /* encode_text */
          252: opcode_builder(Opcode, function() {
            return "e.encode_text(" + this.args() + ")";
          }),
          /* copy_table */
          253: opcode_builder(Opcode, function() {
            return "e.copy_table(" + this.args() + ")";
          }),
          /* print_table */
          254: opcode_builder(Opcode, function() {
            return "e.print_table(" + this.args() + ")";
          }),
          /* check_arg_count */
          255: opcode_builder(Brancher, function(arg) {
            return "e.stack.getUint8(e.frameptr+5)&(1<<(" + arg + "-1))";
          }),
          /* save */
          1e3: V45Save,
          /* restore */
          1001: V45Restore,
          /* log_shift */
          1002: opcode_builder(Storer, function(a, b) {
            return "e.S2U(e.log_shift(" + a + "," + b.U2S() + "))";
          }),
          /* art_shift */
          1003: opcode_builder(Storer, function(a, b) {
            return "e.S2U(e.art_shift(" + a.U2S() + "," + b.U2S() + "))";
          }),
          /* set_font */
          1004: opcode_builder(Storer, function(font) {
            return "e.set_font(" + font + ")";
          }),
          /* save_undo */
          1009: opcode_builder(Storer, function() {
            return "e.save_undo(" + this.next + "," + this.storer.v + ")";
          }),
          // As the standard says calling this without a save point is illegal, we don't need to actually store anything (but it must still be disassembled)
          /* restore_undo */
          1010: opcode_builder(Opcode, function() {
            return "if(e.restore_undo())return";
          }, { storer: 1 }),
          /* print_unicode */
          1011: opcode_builder(Opcode, function(a) {
            return "e.print(1," + a + ")";
          }),
          // Assume we can print and read all unicode characters rather than actually testing
          /* check_unicode */
          1012: opcode_builder(Storer, function() {
            return 3;
          }),
          /* set_true_colour */
          1013: opcode_builder(Opcode, function() {
            return "e.set_true_colour(" + this.args() + ")";
          }),
          /* sound_data */
          1014: Opcode.subClass({ brancher: 1 }),
          // We don't support sounds (but disassemble the branch address)
          /* gestalt */
          1030: opcode_builder(Storer, function() {
            return "e.gestalt(" + this.args() + ")";
          })
          /* parchment */
          //1031: opcode_builder( Storer, function() { return 'e.op_parchment(' + this.args() + ')'; } ),
        };
      };
    }
  });

  // ../../../../../tmp/tmp_ui9n_e9/ifvms/src/zvm/runtime.js
  var require_runtime = __commonJS({
    "../../../../../tmp/tmp_ui9n_e9/ifvms/src/zvm/runtime.js"(exports, module) {
      "use strict";
      var file = require_file();
      var utils = require_utils();
      var extend = utils.extend;
      var U2S = utils.U2S16;
      var S2U = utils.S2U16;
      function clone(obj) {
        const recurse = (obj2) => typeof obj2 === "object" ? clone(obj2) : obj2;
        const newobj = {};
        if (Array.isArray(obj)) {
          return obj.map(recurse);
        }
        for (let prop in obj) {
          if (prop !== "buffer" && prop !== "str") {
            newobj[prop] = recurse(obj[prop]);
          }
        }
        return newobj;
      }
      var littleEndian = function() {
        var testUint8Array = new Uint8Array(2), testUint16Array = new Uint16Array(testUint8Array.buffer);
        testUint16Array[0] = 1;
        return testUint8Array[0] === 1;
      }();
      function fix_stack_endianness(view, start, end, auto) {
        if (littleEndian && !auto) {
          while (start < end) {
            view.setUint16(start, view.getUint16(start, 1));
            start += 2;
          }
        }
      }
      module.exports = {
        art_shift: function(number, places) {
          return places > 0 ? number << places : number >> -places;
        },
        // Call a routine
        call: function(addr, storer, next, args) {
          if (addr === 0) {
            if (storer >= 0) {
              this.variable(storer, 0);
            }
            return this.pc = next;
          }
          this.pc = addr * this.addr_multipler;
          var locals_count = this.m.getUint8(this.pc++), stack = this.stack, i = 0, frameptr = this.frameptr;
          stack.setUint16(frameptr + 6, this.sp);
          this.frames.push(frameptr);
          frameptr = this.frameptr = this.s.byteOffset + this.sp * 2;
          stack.setUint32(frameptr, next << 8);
          stack.setUint8(frameptr + 3, (storer >= 0 ? 0 : 16) | locals_count);
          stack.setUint8(frameptr + 4, storer >= 0 ? storer : 0);
          stack.setUint8(frameptr + 5, (1 << args.length) - 1);
          this.make_stacks();
          this.sp = 0;
          while (i < locals_count) {
            this.l[i] = i < args.length ? args[i] : this.version < 5 ? this.m.getUint16(this.pc + i * 2) : 0;
            i++;
          }
          if (this.version < 5) {
            this.pc += locals_count * 2;
          }
        },
        clear_attr: function(object, attribute) {
          var addr = this.objects + (this.version3 ? 9 : 14) * object + attribute / 8 | 0;
          this.ram.setUint8(addr, this.m.getUint8(addr) & ~(128 >> attribute % 8));
        },
        copy_table: function(first, second, size) {
          size = U2S(size);
          var ram = this.ram, i = 0, allowcorrupt = size < 0;
          size = Math.abs(size);
          if (second === 0) {
            while (i < size) {
              ram.setUint8(first + i++, 0);
            }
            return;
          }
          if (allowcorrupt) {
            while (i < size) {
              ram.setUint8(second + i, this.m.getUint8(first + i++));
            }
          } else {
            ram.setUint8Array(second, this.m.getUint8Array(first, size));
          }
        },
        do_autorestore: function(snapshot) {
          const Glk = this.Glk;
          Glk.restore_allstate(snapshot.glk);
          this.io = snapshot.io;
          const RockBox = new Glk.RefBox();
          let obj;
          while (obj = Glk.glk_window_iterate(obj, RockBox)) {
            if (RockBox.value === 201) {
              this.mainwin = obj;
              if (obj.linebuf) {
                snapshot.read_data.buffer = obj.linebuf;
              }
            }
            if (RockBox.value === 202) {
              this.statuswin = obj;
            }
            if (RockBox.value === 203) {
              this.upperwin = obj;
            }
          }
          obj = null;
          while (obj = Glk.glk_stream_iterate(obj, RockBox)) {
            if (RockBox.value === 210) {
              this.io.streams[2].str = obj;
            }
            if (RockBox.value === 211) {
              this.io.streams[4].str = obj;
            }
          }
          this.restart(1);
          this.restore_file(this.options.Dialog.streaming ? new Uint8Array(snapshot.ram) : Uint8Array.from(snapshot.ram), 1);
          this.read_data = snapshot.read_data;
          this.xorshift_seed = snapshot.xorshift_seed;
        },
        do_autosave: function(save) {
          if (!this.options.Dialog) {
            throw new Error("A reference to Dialog is required");
          }
          let snapshot = null;
          if ((save || 0) >= 0) {
            const ram = this.save_file(this.pc, 1);
            snapshot = {
              glk: this.Glk.save_allstate(),
              io: clone(this.io),
              ram: this.options.Dialog.streaming ? ram : Array.from(new Uint8Array(ram)),
              read_data: clone(this.read_data),
              xorshift_seed: this.xorshift_seed
            };
          }
          this.options.Dialog.autosave_write(this.signature, snapshot);
        },
        encode_text: function(zscii, length, from, target) {
          this.ram.setUint8Array(target, this.encode(this.m.getUint8Array(zscii + from, length)));
        },
        // Access the extension table
        extension_table: function(word, value) {
          var addr = this.extension;
          if (!addr || word > this.extension_count) {
            return 0;
          }
          addr += 2 * word;
          if (value === void 0) {
            return this.m.getUint16(addr);
          }
          this.ram.setUint16(addr, value);
        },
        // Find the address of a property, or given the previous property, the number of the next
        find_prop: function(object, property, prev) {
          var memory = this.m, version3 = this.version3, this_property_byte, this_property, last_property = 0, properties = memory.getUint16(this.objects + (version3 ? 9 : 14) * object + (version3 ? 7 : 12));
          properties += memory.getUint8(properties) * 2 + 1;
          while (1) {
            this_property_byte = memory.getUint8(properties);
            this_property = this_property_byte & (version3 ? 31 : 63);
            if (last_property === prev) {
              return this_property;
            }
            if (this_property === property) {
              return properties + (!version3 && this_property_byte & 128 ? 2 : 1);
            }
            if (this_property < property) {
              return 0;
            }
            last_property = this_property;
            if (version3) {
              properties += (this_property_byte >> 5) + 2;
            } else {
              if (this_property_byte & 128) {
                this_property = memory.getUint8(properties + 1) & 63;
                properties += this_property ? this_property + 2 : 66;
              } else {
                properties += this_property_byte & 64 ? 3 : 2;
              }
            }
          }
        },
        // 1.2 spec @gestalt
        gestalt: function(id) {
          switch (id) {
            case 1:
              return 258;
          }
          return 0;
        },
        // Get the first child of an object
        get_child: function(obj) {
          if (this.version3) {
            return this.m.getUint8(this.objects + 9 * obj + 6);
          } else {
            return this.m.getUint16(this.objects + 14 * obj + 10);
          }
        },
        get_sibling: function(obj) {
          if (this.version3) {
            return this.m.getUint8(this.objects + 9 * obj + 5);
          } else {
            return this.m.getUint16(this.objects + 14 * obj + 8);
          }
        },
        get_parent: function(obj) {
          if (this.version3) {
            return this.m.getUint8(this.objects + 9 * obj + 4);
          } else {
            return this.m.getUint16(this.objects + 14 * obj + 6);
          }
        },
        get_prop: function(object, property) {
          var memory = this.m, addr = this.find_prop(object, property), len;
          if (addr) {
            len = memory.getUint8(addr - 1);
            return memory[(this.version3 ? len >> 5 : len & 64) ? "getUint16" : "getUint8"](addr);
          }
          return memory.getUint16(this.properties + 2 * (property - 1));
        },
        // Get the length of a property
        // This opcode expects the address of the property data, not a property block
        get_prop_len: function(addr) {
          if (addr === 0) {
            return 0;
          }
          var value = this.m.getUint8(addr - 1);
          if (this.version3) {
            return (value >> 5) + 1;
          }
          if (value & 128) {
            value &= 63;
            return value === 0 ? 64 : value;
          }
          return value & 64 ? 2 : 1;
        },
        // Quick hack for @inc/@dec/@inc_chk/@dec_chk
        incdec: function(varnum, change) {
          if (varnum === 0) {
            this.s[this.sp - 1] += change;
            return this.s[this.sp - 1];
          }
          if (--varnum < 15) {
            this.l[varnum] += change;
            return this.l[varnum];
          } else {
            var offset = this.globals + (varnum - 15) * 2;
            this.ram.setUint16(offset, this.m.getUint16(offset) + change);
            return this.ram.getUint16(offset);
          }
        },
        // Indirect variables
        indirect: function(variable, value) {
          if (variable === 0) {
            if (arguments.length > 1) {
              return this.s[this.sp - 1] = value;
            } else {
              return this.s[this.sp - 1];
            }
          }
          return this.variable(variable, value);
        },
        insert_obj: function(obj, dest) {
          this.remove_obj(obj);
          this.set_family(obj, dest, dest, obj, obj, this.get_child(dest));
        },
        // @jeq
        jeq: function() {
          var i = 1;
          while (i < arguments.length) {
            if (arguments[i++] === arguments[0]) {
              return 1;
            }
          }
        },
        jin: function(child, parent) {
          return this.get_parent(child) === parent;
        },
        log: function(message) {
          if (this.options.GlkOte) {
            this.options.GlkOte.log(message);
          }
        },
        log_shift: function(number, places) {
          return places > 0 ? number << places : number >>> -places;
        },
        make_stacks: function() {
          var locals_count = this.stack.getUint8(this.frameptr + 3) & 15;
          this.l = new Uint16Array(this.stack.buffer, this.frameptr + 8, locals_count);
          this.s = new Uint16Array(this.stack.buffer, this.frameptr + 8 + locals_count * 2);
        },
        put_prop: function(object, property, value) {
          var addr = this.find_prop(object, property), len;
          if (addr) {
            len = this.m.getUint8(addr - 1);
            this.ram[(this.version3 ? len >> 5 : len & 64) ? "setUint16" : "setUint8"](addr, value);
          }
        },
        random: function(range) {
          var seed = this.xorshift_seed;
          if (range < 1) {
            this.xorshift_seed = range;
            return 0;
          }
          if (seed === 0) {
            return 1 + Math.random() * range | 0;
          }
          seed ^= seed << 13;
          seed ^= seed >> 17;
          this.xorshift_seed = seed ^= seed << 5;
          return 1 + (seed & 32767) % range;
        },
        remove_obj: function(obj) {
          var parent = this.get_parent(obj), older_sibling, younger_sibling, temp_younger;
          if (parent === 0) {
            return;
          }
          older_sibling = this.get_child(parent);
          younger_sibling = this.get_sibling(obj);
          if (older_sibling === obj) {
            this.set_family(obj, 0, parent, younger_sibling);
          } else {
            while (1) {
              temp_younger = this.get_sibling(older_sibling);
              if (temp_younger === obj) {
                break;
              }
              older_sibling = temp_younger;
            }
            this.set_family(obj, 0, 0, 0, older_sibling, younger_sibling);
          }
        },
        // (Re)start the VM
        restart: function(autorestoring) {
          var ram = this.ram, version = ram.getUint8(0), version3 = version === 3, addr_multipler = version3 ? 2 : version === 8 ? 8 : 4, flags2 = ram.getUint8(17), property_defaults = ram.getUint16(10), extension = version > 4 ? ram.getUint16(54) : 0, stack = utils.MemoryView(this.options.stack_len);
          ram.setUint8Array(0, this.origram);
          ram.setUint8(17, flags2);
          extend(this, {
            // Locals and stacks of various kinds
            stack,
            frameptr: 0,
            frames: [],
            s: new Uint16Array(stack.buffer, 8),
            sp: 0,
            l: [],
            undo: [],
            undo_len: 0,
            glk_blocking_call: null,
            // Get some header variables
            version,
            version3,
            pc: ram.getUint16(6),
            properties: property_defaults,
            objects: property_defaults + (version3 ? 53 : 112),
            // 62-9 or 126-14 - if we take this now then we won't need to always decrement the object number
            globals: ram.getUint16(12),
            // staticmem: set in prepare()
            eof: (ram.getUint16(26) || 65536) * addr_multipler,
            extension,
            extension_count: extension ? this.m.getUint16(extension) : 0,
            // Routine and string multiplier
            addr_multipler,
            // Opcodes for this version of the Z-Machine
            opcodes: require_opcodes()(version)
          });
          this.init_text();
          if (!autorestoring) {
            this.init_io();
          }
          this.update_header();
        },
        // Request a restore
        restore: function(pc) {
          this.pc = pc;
          this.fileref_create_by_prompt({
            func: "restore",
            mode: 2,
            usage: 1
          });
        },
        restore_file: function(data, autorestoring) {
          var ram = this.ram, quetzal = new file.Quetzal(data), qmem = quetzal.memory, stack = this.stack, flags2 = ram.getUint8(17), temp, i = 0, j = 0;
          if (ram.getUint16(2) !== quetzal.release || ram.getUint16(28) !== quetzal.checksum) {
            return 0;
          }
          while (i < 6) {
            if (ram.getUint8(18 + i) !== quetzal.serial[i++]) {
              return 0;
            }
          }
          i = 0;
          ram.setUint8Array(0, this.origram);
          if (quetzal.compressed) {
            while (i < qmem.length) {
              temp = qmem[i++];
              if (temp === 0) {
                j += 1 + qmem[i++];
              } else {
                ram.setUint8(j, temp ^ this.origram[j++]);
              }
            }
          } else {
            ram.setUint8Array(0, qmem);
          }
          ram.setUint8(17, flags2);
          stack.setUint8Array(0, quetzal.stacks);
          this.frames = [];
          i = 0;
          while (i < quetzal.stacks.byteLength) {
            this.frameptr = i;
            this.frames.push(i);
            fix_stack_endianness(stack, j = i + 8, j += (stack.getUint8(i + 3) & 15) * 2, autorestoring);
            fix_stack_endianness(stack, j, j += stack.getUint16(i + 6) * 2, autorestoring);
            i = j;
          }
          this.frames.pop();
          this.sp = stack.getUint16(this.frameptr + 6);
          this.make_stacks();
          this.pc = quetzal.pc;
          this.update_header();
          if (this.version3) {
            this.split_window(0);
          }
          return 2;
        },
        restore_undo: function() {
          if (this.undo.length === 0) {
            return 0;
          }
          var state = this.undo.pop();
          this.frameptr = state.frameptr;
          this.pc = state.pc;
          this.undo_len -= state.ram.byteLength + state.stack.byteLength;
          state.ram[17] = this.m.getUint8(17);
          this.ram.setUint8Array(0, state.ram);
          this.frames = state.frames;
          this.sp = state.sp;
          this.stack.setUint8Array(0, state.stack);
          this.make_stacks();
          this.variable(state.var, 2);
          return 1;
        },
        // Return from a routine
        ret: function(result) {
          var stack = this.stack, frameptr = this.frameptr, storer = stack.getUint8(frameptr + 3) & 16 ? -1 : stack.getUint8(frameptr + 4);
          this.pc = stack.getUint32(frameptr) >> 8;
          frameptr = this.frameptr = this.frames.pop();
          this.make_stacks();
          this.sp = stack.getUint16(frameptr + 6);
          if (storer >= 0) {
            this.variable(storer, result || 0);
          }
        },
        // pc is the address of the storer operand (or branch in v3)
        save: function(pc) {
          this.pc = pc;
          this.fileref_create_by_prompt({
            func: "save",
            mode: 1,
            usage: 1
          });
        },
        save_file: function(pc, autosaving) {
          var memory = this.m, quetzal = new file.Quetzal(), stack = utils.MemoryView(this.stack.buffer.slice()), zeroes = 0, i, j, frameptr = this.frameptr, abyte;
          quetzal.release = memory.getUint16(2);
          quetzal.serial = memory.getUint8Array(18, 6);
          quetzal.checksum = memory.getUint16(28);
          quetzal.pc = pc;
          if (autosaving) {
            quetzal.memory = this.m.getUint8Array(0, this.staticmem);
          } else {
            const compressed_mem = [];
            quetzal.compressed = 1;
            for (i = 0; i < this.staticmem; i++) {
              abyte = memory.getUint8(i) ^ this.origram[i];
              if (abyte === 0) {
                if (++zeroes === 256) {
                  compressed_mem.push(0, 255);
                  zeroes = 0;
                }
              } else {
                if (zeroes) {
                  compressed_mem.push(0, zeroes - 1);
                  zeroes = 0;
                }
                compressed_mem.push(abyte);
              }
            }
            quetzal.memory = compressed_mem;
          }
          stack.setUint16(frameptr + 6, this.sp);
          if (littleEndian && !autosaving) {
            const frames = this.frames.slice();
            frames.push(frameptr);
            for (i = 0; i < frames.length; i++) {
              frameptr = frames[i];
              fix_stack_endianness(stack, j = frameptr + 8, j += (stack.getUint8(frameptr + 3) & 15) * 2);
              fix_stack_endianness(stack, j, j += stack.getUint16(frameptr + 6) * 2);
            }
          }
          quetzal.stacks = stack.getUint8Array(0, this.frameptr + 8 + (stack.getUint8(frameptr + 3) & 15) * 2 + this.sp * 2);
          return quetzal.write();
        },
        save_restore_handler: function(str) {
          var memory = this.m, Glk = this.Glk, result = 0, buffer = [], temp, iftrue, offset;
          if (str) {
            if (this.fileref_data.func === "save") {
              Glk.glk_put_buffer_stream(str, new Uint8Array(this.save_file(this.pc)));
              result = 1;
            } else {
              buffer = new Uint8Array(128 * 1024);
              Glk.glk_get_buffer_stream(str, buffer);
              result = this.restore_file(buffer.buffer);
            }
            Glk.glk_stream_close(str);
          }
          if (this.version3) {
            temp = memory.getUint8(this.pc++);
            iftrue = temp & 128;
            offset = temp & 64 ? (
              // single byte address
              temp & 63
            ) : (
              // word address, but first get the second byte of it
              (temp << 8 | memory.getUint8(this.pc++)) << 18 >> 18
            );
            if (!result === !iftrue) {
              if (offset === 0 || offset === 1) {
                this.ret(offset);
              } else {
                this.pc += offset - 2;
              }
            }
          } else {
            this.variable(memory.getUint8(this.pc++), result);
          }
        },
        save_undo: function(pc, variable) {
          var state;
          if (this.undo_len > this.options.undo_len) {
            state = this.undo.shift();
            this.undo_len -= state.ram.byteLength + state.stack.byteLength;
          }
          state = {
            frameptr: this.frameptr,
            frames: this.frames.slice(),
            pc,
            ram: this.m.getUint8Array(0, this.staticmem),
            sp: this.sp,
            stack: this.stack.getUint8Array(0, this.s.byteOffset + this.sp * 2),
            var: variable
          };
          this.undo_len += state.ram.byteLength + state.stack.byteLength;
          this.undo.push(state);
          return 1;
        },
        scan_table: function(key, addr, length, form) {
          form = form || 130;
          var memoryfunc = form & 128 ? "getUint16" : "getUint8";
          form &= 127;
          length = addr + length * form;
          while (addr < length) {
            if (this.m[memoryfunc](addr) === key) {
              return addr;
            }
            addr += form;
          }
          return 0;
        },
        set_attr: function(object, attribute) {
          var addr = this.objects + (this.version3 ? 9 : 14) * object + attribute / 8 | 0;
          this.ram.setUint8(addr, this.m.getUint8(addr) | 128 >> attribute % 8);
        },
        set_family: function(obj, newparent, parent, child, bigsis, lilsis) {
          var ram = this.ram, objects = this.objects;
          if (this.version3) {
            ram.setUint8(objects + 9 * obj + 4, newparent);
            if (parent) {
              ram.setUint8(objects + 9 * parent + 6, child);
            }
            if (bigsis) {
              ram.setUint8(objects + 9 * bigsis + 5, lilsis);
            }
          } else {
            ram.setUint16(objects + 14 * obj + 6, newparent);
            if (parent) {
              ram.setUint16(objects + 14 * parent + 10, child);
            }
            if (bigsis) {
              ram.setUint16(objects + 14 * bigsis + 8, lilsis);
            }
          }
        },
        test: function(bitmap, flag) {
          return (bitmap & flag) === flag;
        },
        test_attr: function(object, attribute) {
          return this.m.getUint8(this.objects + (this.version3 ? 9 : 14) * object + attribute / 8 | 0) << attribute % 8 & 128;
        },
        // Read or write a variable
        variable: function(variable, value) {
          var havevalue = value !== void 0, offset;
          if (variable === 0) {
            if (havevalue) {
              this.s[this.sp++] = value;
            } else {
              return this.s[--this.sp];
            }
          } else if (--variable < 15) {
            if (havevalue) {
              this.l[variable] = value;
            } else {
              return this.l[variable];
            }
          } else {
            offset = this.globals + (variable - 15) * 2;
            if (havevalue) {
              this.ram.setUint16(offset, value);
            } else {
              return this.m.getUint16(offset);
            }
          }
          return value;
        },
        // Utilities for signed arithmetic
        U2S,
        S2U
      };
    }
  });

  // ../../../../../tmp/tmp_ui9n_e9/ifvms/src/zvm/text.js
  var require_text = __commonJS({
    "../../../../../tmp/tmp_ui9n_e9/ifvms/src/zvm/text.js"(exports, module) {
      module.exports = {
        init_text: function() {
          var self = this, memory = this.m, alphabet_addr = this.version > 4 && memory.getUint16(52), unicode_addr = this.extension_table(3), unicode_len = unicode_addr && memory.getUint8(unicode_addr++);
          this.abbr_addr = memory.getUint16(24);
          function make_alphabet(data) {
            var alphabets = [[], [], []], i = 0;
            while (i < 78) {
              alphabets[i / 26 | 0][i % 26] = data[i++];
            }
            alphabets[2][1] = 13;
            self.alphabets = alphabets;
          }
          function make_unicode(data) {
            var table = { 13: "\r" }, reverse = { 13: 13 }, i = 0;
            while (i < data.length) {
              table[155 + i] = String.fromCharCode(data[i]);
              reverse[data[i]] = 155 + i++;
            }
            i = 32;
            while (i < 127) {
              table[i] = String.fromCharCode(i);
              reverse[i] = i++;
            }
            self.unicode_table = table;
            self.reverse_unicode_table = reverse;
          }
          make_alphabet(alphabet_addr ? memory.getUint8Array(alphabet_addr, 78) : this.text_to_zscii(`abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ \r0123456789.,!?_#'"/\\-:()`, 1));
          make_unicode(unicode_addr ? memory.getUint16Array(unicode_addr, unicode_len) : this.text_to_zscii(unescape("%E4%F6%FC%C4%D6%DC%DF%BB%AB%EB%EF%FF%CB%CF%E1%E9%ED%F3%FA%FD%C1%C9%CD%D3%DA%DD%E0%E8%EC%F2%F9%C0%C8%CC%D2%D9%E2%EA%EE%F4%FB%C2%CA%CE%D4%DB%E5%C5%F8%D8%E3%F1%F5%C3%D1%D5%E6%C6%E7%C7%FE%F0%DE%D0%A3%u0153%u0152%A1%BF"), 1));
          this.dictionaries = {};
          this.dict = memory.getUint16(8);
          this.parse_dict(this.dict);
        },
        // Decode Z-chars into ZSCII and then Unicode
        decode: function(addr, length) {
          var memory = this.m, start_addr = addr, temp, buffer = [], i = 0, zchar, alphabet = 0, result = [], resulttexts = [], usesabbr, unicodecount = 0;
          if (this.jit[addr]) {
            return this.jit[addr];
          }
          length = length ? length + addr : this.eof;
          while (addr < length) {
            temp = memory.getUint16(addr);
            addr += 2;
            buffer.push(temp >> 10 & 31, temp >> 5 & 31, temp & 31);
            if (temp & 32768) {
              break;
            }
          }
          while (i < buffer.length) {
            zchar = buffer[i++];
            if (zchar === 0) {
              result.push(32);
            } else if (zchar < 4) {
              usesabbr = 1;
              result.push(-1);
              resulttexts.push("\uE000+this.abbr(" + (32 * (zchar - 1) + buffer[i++]) + ")+\uE000");
            } else if (zchar < 6) {
              alphabet = zchar;
            } else if (alphabet === 2 && zchar === 6) {
              if (i + 1 < buffer.length) {
                result.push(buffer[i++] << 5 | buffer[i++]);
              }
            } else if (zchar < 32) {
              result.push(this.alphabets[alphabet][zchar - 6]);
            }
            alphabet = alphabet < 4 ? 0 : alphabet - 3;
            if (i % 3 === 0) {
              i += unicodecount;
              unicodecount = 0;
            }
          }
          result = this.zscii_to_text(result, resulttexts);
          if (usesabbr) {
            result = {
              toString: Function('return"' + result.replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\r/g, "\\r").replace(/\uE000/g, '"') + '"').bind(this)
            };
          }
          if (start_addr >= this.staticmem) {
            this.jit[start_addr] = result;
          }
          return result;
        },
        // Encode ZSCII into Z-chars
        encode: function(zscii) {
          var alphabets = this.alphabets, zchars = [], word_len = this.version3 ? 6 : 9, i = 0, achar, temp, result = [];
          while (zchars.length < word_len) {
            achar = zscii[i++];
            if (achar === 32) {
              zchars.push(0);
            } else if ((temp = alphabets[0].indexOf(achar)) >= 0) {
              zchars.push(temp + 6);
            } else if ((temp = alphabets[1].indexOf(achar)) >= 0) {
              zchars.push(4, temp + 6);
            } else if ((temp = alphabets[2].indexOf(achar)) >= 0) {
              zchars.push(5, temp + 6);
            } else if (achar === void 0) {
              zchars.push(5);
            } else {
              zchars.push(5, 6, achar >> 5, achar & 31);
            }
          }
          zchars.length = word_len;
          i = 0;
          while (i < word_len) {
            result.push(zchars[i++] << 2 | zchars[i] >> 3, (zchars[i++] & 7) << 5 | zchars[i++]);
          }
          result[result.length - 2] |= 128;
          return result;
        },
        // In these two functions zscii means an array of ZSCII codes and text means a regular Javascript unicode string
        zscii_to_text: function(zscii, texts) {
          var i = 0, l = zscii.length, charr, j = 0, result = "";
          while (i < l) {
            charr = zscii[i++];
            if (charr === -1) {
              result += texts[j++];
            }
            if (charr = this.unicode_table[charr]) {
              result += charr;
            }
          }
          return result;
        },
        // If the second argument is set then don't use the unicode table
        text_to_zscii: function(text, notable) {
          var array = [], i = 0, l = text.length, charr;
          while (i < l) {
            charr = text.charCodeAt(i++);
            if (!notable) {
              charr = this.reverse_unicode_table[charr] || 63;
            }
            array.push(charr);
          }
          return array;
        },
        // Parse and cache a dictionary
        parse_dict: function(addr) {
          var memory = this.m, addr_start = addr, dict = {}, entry_len, endaddr, seperators_len = memory.getUint8(addr++);
          dict.separators = Array.prototype.slice.call(memory.getUint8Array(addr, seperators_len));
          addr += seperators_len;
          entry_len = memory.getUint8(addr++);
          endaddr = addr + 2 + entry_len * memory.getUint16(addr);
          addr += 2;
          while (addr < endaddr) {
            dict[Array.prototype.toString.call(memory.getUint8Array(addr, this.version3 ? 4 : 6))] = addr;
            addr += entry_len;
          }
          this.dictionaries[addr_start] = dict;
          return dict;
        },
        // Print an abbreviation
        abbr: function(abbrnum) {
          return this.decode(this.m.getUint16(this.abbr_addr + 2 * abbrnum) * 2);
        },
        // Tokenise a text
        tokenise: function(bufaddr, parseaddr, dictionary, flag) {
          dictionary = dictionary || this.dict;
          dictionary = this.dictionaries[dictionary] || this.parse_dict(dictionary);
          var memory = this.m, ram = this.ram, bufferlength = 1e3, i = 1, letter, separators = dictionary.separators, word, words = [], max_words, dictword, wordcount = 0;
          if (this.version > 4) {
            bufferlength = memory.getUint8(bufaddr + i++) + 2;
          }
          while (i < bufferlength) {
            letter = memory.getUint8(bufaddr + i);
            if (letter === 0) {
              break;
            } else if (letter === 32 || separators.indexOf(letter) >= 0) {
              if (letter !== 32) {
                words.push([[letter], i]);
              }
              word = null;
            } else {
              if (!word) {
                words.push([[], i]);
                word = words[words.length - 1][0];
              }
              word.push(letter);
            }
            i++;
          }
          max_words = Math.min(words.length, memory.getUint8(parseaddr));
          while (wordcount < max_words) {
            dictword = dictionary["" + this.encode(words[wordcount][0])];
            if (!flag || dictword) {
              ram.setUint16(parseaddr + 2 + wordcount * 4, dictword || 0);
              ram.setUint8(parseaddr + 4 + wordcount * 4, words[wordcount][0].length);
              ram.setUint8(parseaddr + 5 + wordcount * 4, words[wordcount][1]);
            }
            wordcount++;
          }
          ram.setUint8(parseaddr + 1, wordcount);
        }
      };
    }
  });

  // ../../../../../tmp/tmp_ui9n_e9/ifvms/src/zvm/io.js
  var require_io = __commonJS({
    "../../../../../tmp/tmp_ui9n_e9/ifvms/src/zvm/io.js"(exports, module) {
      "use strict";
      var utils = require_utils();
      var U2S = utils.U2S16;
      var ZSCII_keyCodes = function() {
        var codes = {
          4294967289: 8,
          // delete/backspace
          4294967290: 13,
          // enter
          4294967288: 27,
          // escape
          4294967292: 129,
          // up
          4294967291: 130,
          // down
          4294967294: 131,
          // left
          4294967293: 132,
          // right
          4294967283: 146,
          // End / key pad 1
          4294967285: 148,
          // PgDn / key pad 3
          4294967284: 152,
          // Home / key pad 7
          4294967286: 154
          // PgUp / key pad 9
        }, i = 0;
        while (i < 12) {
          codes[4294967279 - i] = 133 + i++;
        }
        return codes;
      }();
      var style_mappings = [0, 2, 1, 10, 4, 9, 5, 6];
      function convert_true_colour(colour) {
        const from5to8 = [
          0,
          8,
          16,
          25,
          33,
          41,
          49,
          58,
          66,
          74,
          82,
          90,
          99,
          107,
          115,
          123,
          132,
          140,
          148,
          156,
          165,
          173,
          181,
          189,
          197,
          206,
          214,
          222,
          230,
          239,
          247,
          255
        ];
        return from5to8[colour & 31] << 16 | from5to8[(colour & 992) >> 5] << 8 | from5to8[(colour & 31744) >> 10];
      }
      var zcolours = [
        65534,
        // Current
        65535,
        // Default
        0,
        // Black
        29,
        // Red
        832,
        // Green
        957,
        // Yellow
        22944,
        // Blue
        31775,
        // Magenta
        30624,
        // Cyan
        32767,
        // White
        23254,
        // Light grey
        17969,
        // Medium grey
        11627
        // Dark grey
      ];
      module.exports = {
        init_io: function() {
          this.io = {
            reverse: 0,
            bold: 0,
            italic: 0,
            bg: -1,
            fg: -1,
            // A variable for whether we are outputing in a monospaced font. If non-zero then we are
            // Bit 0 is for @set_style, bit 1 for the header, and bit 2 for @set_font
            mono: this.m.getUint8(17) & 2,
            // A variable for checking whether the transcript bit has been changed
            transcript: this.m.getUint8(17) & 1,
            // Index 0 is input stream 1, the output streams follow
            streams: [0, 1, {}, [], {}],
            currentwin: 0,
            // Use Zarf's algorithm for the upper window
            // http://eblong.com/zarf/glk/quote-box.html
            // Implemented in fix_upper_window() and split_window()
            height: 0,
            // What the VM thinks the height is
            glkheight: 0,
            // Actual height of the Glk window
            maxheight: 0,
            // Height including quote boxes etc
            seenheight: 0,
            // Last height the player saw
            width: 0,
            row: 0,
            col: 0
          };
          this.open_windows();
        },
        erase_line: function(value) {
          if (value === 1) {
            var io = this.io, row = io.row, col = io.col;
            this._print(Array(io.width - io.col + 1).join(" "));
            this.set_cursor(row, col);
          }
        },
        erase_window: function(window) {
          if (window < 1) {
            this.Glk.glk_window_clear(this.mainwin);
            if (this.io.bg >= 0) {
              this.Glk.glk_stylehint_set(3, 0, 8, this.io.bg);
            } else if (this.io.bg === -1) {
              this.Glk.glk_stylehint_clear(3, 0, 8);
            }
          }
          if (window !== 0) {
            if (window === -1) {
              this.split_window(0);
            }
            if (this.upperwin) {
              this.Glk.glk_window_clear(this.upperwin);
              this.set_cursor(0, 0);
            }
          }
        },
        fileref_create_by_prompt: function(data) {
          if (typeof data.run === "undefined") {
            data.run = 1;
          }
          this.fileref_data = data;
          this.glk_blocking_call = "fileref_create_by_prompt";
          this.Glk.glk_fileref_create_by_prompt(data.usage, data.mode, data.rock || 0);
        },
        // Fix the upper window height before an input event
        fix_upper_window: function() {
          var Glk = this.Glk, io = this.io;
          if (io.seenheight >= io.maxheight) {
            io.maxheight = io.height;
          }
          if (this.upperwin) {
            if (io.maxheight === 0) {
              Glk.glk_window_close(this.upperwin);
              this.upperwin = null;
            } else if (io.maxheight !== io.glkheight) {
              Glk.glk_window_set_arrangement(Glk.glk_window_get_parent(this.upperwin), 18, io.maxheight, null);
            }
            io.glkheight = io.maxheight;
          }
          io.seenheight = io.maxheight;
          io.maxheight = io.height;
        },
        format: function() {
          this.Glk.glk_set_style(style_mappings[!!this.io.mono | this.io.italic | this.io.bold]);
          if (this.Glk.glk_gestalt(4352, 0)) {
            this.Glk.garglk_set_reversevideo(this.io.reverse);
          }
        },
        get_cursor: function(array) {
          this.ram.setUint16(array, this.io.row + 1);
          this.ram.setUint16(array + 2, this.io.col + 1);
        },
        // Handle char input
        handle_char_input: function(charcode) {
          var stream4 = this.io.streams[4], code = ZSCII_keyCodes[charcode] || this.reverse_unicode_table[charcode] || 63;
          this.variable(this.read_data.storer, code);
          if (stream4.mode === 1) {
            stream4.cache += code;
          }
          if (stream4.mode === 2) {
            this.Glk.glk_put_char_stream_uni(stream4.str, code);
          }
        },
        // Handle the result of glk_fileref_create_by_prompt()
        handle_create_fileref: function(fref) {
          var Glk = this.Glk, data = this.fileref_data, str;
          if (fref) {
            if (data.unicode) {
              str = Glk.glk_stream_open_file_uni(fref, data.mode, data.rock || 0);
            } else {
              str = Glk.glk_stream_open_file(fref, data.mode, data.rock || 0);
            }
            Glk.glk_fileref_destroy(fref);
          }
          if (data.func === "restore" || data.func === "save") {
            this.save_restore_handler(str);
          }
          if (data.func === "input_stream") {
            this.io.streams[0] = str;
          }
          if (data.func === "output_stream") {
            this.output_stream_handler(str);
          }
          return data.run;
        },
        // Handle line input
        handle_line_input: function(len, terminator) {
          var ram = this.ram, options = this.read_data, streams = this.io.streams, command = String.fromCharCode.apply(null, options.buffer.slice(0, len)) + "\n", response = this.text_to_zscii(command.slice(0, -1).toLowerCase());
          if (streams[2].mode === 1) {
            streams[2].cache += command;
          }
          if (streams[2].mode === 2) {
            this.Glk.glk_put_jstring_stream(streams[2].str, command);
          }
          if (streams[4].mode === 1) {
            streams[4].cache += command;
          }
          if (streams[4].mode === 2) {
            this.Glk.glk_put_jstring_stream(streams[4].str, command);
          }
          if (this.version < 5) {
            response.push(0);
            ram.setUint8Array(options.bufaddr + 1, response);
          } else {
            ram.setUint8(options.bufaddr + 1, len);
            ram.setUint8Array(options.bufaddr + 2, response);
            this.variable(options.storer, isNaN(terminator) ? 13 : terminator);
          }
          if (options.parseaddr) {
            this.tokenise(options.bufaddr, options.parseaddr);
          }
        },
        input_stream: function(stream) {
          var io = this.io;
          if (stream && !io.streams[0]) {
            this.fileref_create_by_prompt({
              func: "input_stream",
              mode: 2,
              rock: 212,
              unicode: 1,
              usage: 259
            });
          }
          if (!stream && io.streams[0]) {
            this.Glk.glk_stream_close(io.streams[0]);
            io.streams[0] = 0;
          }
        },
        // Open windows
        open_windows: function() {
          const Glk = this.Glk;
          if (!this.mainwin) {
            const styles_to_reset = [1, 2, 4, 5, 6, 9, 10];
            for (let i = 0; i < 7; i++) {
              Glk.glk_stylehint_set(0, styles_to_reset[i], 3, 0);
              Glk.glk_stylehint_set(0, styles_to_reset[i], 4, 0);
              Glk.glk_stylehint_set(0, styles_to_reset[i], 5, 0);
              Glk.glk_stylehint_set(0, styles_to_reset[i], 6, 1);
            }
            Glk.glk_stylehint_set(0, 4, 4, 1);
            Glk.glk_stylehint_set(0, 1, 5, 1);
            Glk.glk_stylehint_set(0, 5, 4, 1);
            Glk.glk_stylehint_set(0, 5, 5, 1);
            Glk.glk_stylehint_set(0, 2, 6, 0);
            Glk.glk_stylehint_set(0, 9, 4, 1);
            Glk.glk_stylehint_set(0, 9, 6, 0);
            Glk.glk_stylehint_set(0, 10, 5, 1);
            Glk.glk_stylehint_set(0, 10, 6, 0);
            Glk.glk_stylehint_set(0, 6, 4, 1);
            Glk.glk_stylehint_set(0, 6, 5, 1);
            Glk.glk_stylehint_set(0, 6, 6, 0);
            this.mainwin = Glk.glk_window_open(0, 0, 0, 3, 201);
            Glk.glk_set_window(this.mainwin);
            if (this.version3) {
              this.statuswin = Glk.glk_window_open(this.mainwin, 18, 1, 4, 202);
              if (this.statuswin && this.Glk.glk_gestalt(4352, 0)) {
                Glk.garglk_set_reversevideo_stream(Glk.glk_window_get_stream(this.statuswin), 1);
              }
            }
          } else {
            Glk.glk_stylehint_clear(0, 0, 8);
            if (this.Glk.glk_gestalt(4352, 0)) {
              Glk.garglk_set_zcolors_stream(this.mainwin.str, this.io.fg, this.io.bg);
            }
            Glk.glk_window_clear(this.mainwin);
            if (this.upperwin) {
              Glk.glk_window_close(this.upperwin);
              this.upperwin = null;
            }
          }
        },
        // Manage output streams
        output_stream: function(stream, addr, called_from_print) {
          var ram = this.ram, streams = this.io.streams, data, text;
          stream = U2S(stream);
          if (stream === 1) {
            streams[1] = 1;
          }
          if (stream === -1) {
            streams[1] = 0;
          }
          if (stream === 2 && !streams[2].mode) {
            this.fileref_create_by_prompt({
              func: "output_stream",
              mode: 5,
              rock: 210,
              run: !called_from_print,
              str: 2,
              unicode: 1,
              usage: 258
            });
            streams[2].cache = "";
            streams[2].mode = 1;
            if (!called_from_print) {
              this.stop = 1;
            }
          }
          if (stream === -2) {
            ram.setUint8(17, ram.getUint8(17) & 254);
            if (streams[2].mode === 2) {
              this.Glk.glk_stream_close(streams[2].str);
            }
            streams[2].mode = this.io.transcript = 0;
          }
          if (stream === 3) {
            streams[3].unshift([addr, ""]);
          }
          if (stream === -3) {
            data = streams[3].shift();
            text = this.text_to_zscii(data[1]);
            ram.setUint16(data[0], text.length);
            ram.setUint8Array(data[0] + 2, text);
          }
          if (stream === 4 && !streams[4].mode) {
            this.fileref_create_by_prompt({
              func: "output_stream",
              mode: 5,
              rock: 211,
              str: 4,
              unicode: 1,
              usage: 259
            });
            streams[4].cache = "";
            streams[4].mode = 1;
            this.stop = 1;
          }
          if (stream === -4) {
            if (streams[4].mode === 2) {
              this.Glk.glk_stream_close(streams[4].str);
            }
            streams[4].mode = 0;
          }
        },
        output_stream_handler: function(str) {
          var ram = this.ram, streams = this.io.streams, data = this.fileref_data;
          if (data.str === 2) {
            ram.setUint8(17, ram.getUint8(17) & 254 | (str ? 1 : 0));
            if (str) {
              streams[2].mode = 2;
              streams[2].str = str;
              this.io.transcript = 1;
              if (streams[2].cache) {
                this.Glk.glk_put_jstring_stream(streams[2].str, streams[2].cache);
              }
            } else {
              streams[2].mode = this.io.transcript = 0;
            }
          }
          if (data.str === 4) {
            if (str) {
              streams[4].mode = 2;
              streams[4].str = str;
              if (streams[4].cache) {
                this.Glk.glk_put_jstring_stream(streams[4].str, streams[4].cache);
              }
            } else {
              streams[4].mode = 0;
            }
          }
        },
        // Print text!
        _print: function(text) {
          var Glk = this.Glk, io = this.io, i = 0;
          if (io.streams[3].length) {
            io.streams[3][0][1] += text;
          } else {
            text = text.replace(/\r/g, "\n");
            if ((this.m.getUint8(17) & 1) !== io.transcript) {
              this.output_stream(io.transcript ? -2 : 2, 0, 1);
            }
            if ((this.m.getUint8(17) & 2) !== (io.mono & 2)) {
              io.mono ^= 2;
              this.format();
            }
            if (io.currentwin && this.upperwin) {
              while (i < text.length && io.row < io.height) {
                Glk.glk_put_jstring(text[i++]);
                io.col++;
                if (io.col === io.width) {
                  io.col = 0;
                  io.row++;
                }
              }
            } else if (!io.currentwin) {
              if (io.streams[1]) {
                Glk.glk_put_jstring(text);
              }
              if (io.streams[2].mode === 1) {
                io.streams[2].cache += text;
              }
              if (io.streams[2].mode === 2) {
                Glk.glk_put_jstring_stream(io.streams[2].str, text);
              }
            }
          }
        },
        // Print many things
        print: function(type, val) {
          var proptable, result;
          if (type === 0) {
            result = val;
          }
          if (type === 1) {
            result = String.fromCharCode(val);
          }
          if (type === 2) {
            result = this.jit[val] || this.decode(val);
          }
          if (type === 3) {
            proptable = this.m.getUint16(this.objects + (this.version3 ? 9 : 14) * val + (this.version3 ? 7 : 12));
            result = this.decode(proptable + 1, this.m.getUint8(proptable) * 2);
          }
          if (type === 4) {
            if (!this.unicode_table[val]) {
              return;
            }
            result = this.unicode_table[val];
          }
          this._print("" + result);
        },
        print_table: function(zscii, width, height, skip) {
          height = height || 1;
          skip = skip || 0;
          var i = 0;
          while (i++ < height) {
            this._print(this.zscii_to_text(this.m.getUint8Array(zscii, width)) + (i < height ? "\r" : ""));
            zscii += width + skip;
          }
        },
        // Process CSS default colours
        /*process_colours: function()
        	{
        		// Convert RGB to a Z-Machine true colour
        		// RGB is a css colour code. rgb(), #000000 and #000 formats are supported.
        		function convert_RGB( code )
        		{
        			var round = Math.round,
        			data = /(\d+),\s*(\d+),\s*(\d+)|#(\w{1,2})(\w{1,2})(\w{1,2})/.exec( code ),
        			result;
        
        			// Nice rgb() code
        			if ( data[1] )
        			{
        				result =  [ data[1], data[2], data[3] ];
        			}
        			else
        			{
        				// Messy CSS colour code
        				result = [ parseInt( data[4], 16 ), parseInt( data[5], 16 ), parseInt( data[6], 16 ) ];
        				// Stretch out compact #000 codes to their full size
        				if ( code.length === 4 )
        				{
        					result = [ result[0] << 4 | result[0], result[1] << 4 | result[1], result[2] << 4 | result[2] ];
        				}
        			}
        
        			// Convert to a 15bit colour
        			return round( result[2] / 8.226 ) << 10 | round( result[1] / 8.226 ) << 5 | round( result[0] / 8.226 );
        		}
        
        		// Standard colours
        		var colours = [
        			0xFFFE, // Current
        			0xFFFF, // Default
        			0x0000, // Black
        			0x001D, // Red
        			0x0340, // Green
        			0x03BD, // Yellow
        			0x59A0, // Blue
        			0x7C1F, // Magenta
        			0x77A0, // Cyan
        			0x7FFF, // White
        			0x5AD6, // Light grey
        			0x4631, // Medium grey
        			0x2D6B,	 // Dark grey
        		],
        
        		// Start with CSS colours provided by the runner
        		fg_css = this.options.fgcolour,
        		bg_css = this.options.bgcolour,
        		// Convert to true colour for storing in the header
        		fg_true = fg_css ? convert_RGB( fg_css ) : 0xFFFF,
        		bg_true = bg_css ? convert_RGB( bg_css ) : 0xFFFF,
        		// Search the list of standard colours
        		fg = colours.indexOf( fg_true ),
        		bg = colours.indexOf( bg_true );
        		// ZVMUI must have colours for reversing text, even if we don't write them to the header
        		// So use the given colours or assume black on white
        		if ( fg < 2 )
        		{
        			fg = fg_css || 2;
        		}
        		if ( bg < 2 )
        		{
        			bg = bg_css || 9;
        		}
        
        		utils.extend( this.options, {
        			fg: fg,
        			bg: bg,
        			fg_true: fg_true,
        			bg_true: bg_true,
        		});
        	},*/
        // Request line input
        read: function(storer, text, parse, time, routine) {
          var len = this.m.getUint8(text), initiallen = 0, buffer, input_stream1_len;
          if (this.version3) {
            this.v3_status();
          }
          if (this.version < 5) {
            len--;
          }
          buffer = Array(len);
          buffer.fill(0);
          this.read_data = {
            buffer,
            bufaddr: text,
            // text-buffer
            parseaddr: parse,
            // parse-buffer
            routine,
            storer,
            time
          };
          if (this.io.streams[0]) {
            input_stream1_len = this.Glk.glk_get_line_stream_uni(this.io.streams[0], buffer);
            if (buffer[input_stream1_len - 1] === 10) {
              input_stream1_len--;
            }
            if (input_stream1_len) {
              this._print(String.fromCharCode.apply(null, buffer.slice(0, input_stream1_len)) + "\n");
              this.handle_line_input(input_stream1_len);
              return this.stop = 0;
            } else {
              this.input_stream(0);
            }
          }
          this.Glk.glk_request_line_event_uni(this.io.currentwin ? this.upperwin : this.mainwin, buffer, initiallen);
          this.fix_upper_window();
        },
        // Request character input
        read_char: function(storer, one, time, routine) {
          if (this.io.streams[0]) {
            var code = this.Glk.glk_get_char_stream_uni(this.io.streams[0]);
            if (code === -1) {
              this.input_stream(0);
            } else {
              this.variable(storer, code);
              return this.stop = 0;
            }
          }
          this.read_data = {
            routine,
            storer,
            time
          };
          this.Glk.glk_request_char_event_uni(this.io.currentwin ? this.upperwin : this.mainwin);
          this.fix_upper_window();
        },
        set_colour: function(foreground, background) {
          this.set_true_colour(zcolours[foreground], zcolours[background]);
        },
        // Note that row and col must be decremented in JIT code
        set_cursor: function(row, col) {
          var io = this.io;
          if (!io.currentwin) {
            return;
          }
          if (row >= io.height) {
            this.split_window(row + 1);
          }
          if (this.upperwin && row >= 0 && col >= 0 && col < io.width) {
            this.Glk.glk_window_move_cursor(this.upperwin, col, row);
            io.row = row;
            io.col = col;
          }
        },
        set_font: function(font) {
          var returnval = this.io.mono & 4 ? 4 : 1;
          if (font === 0) {
            return returnval;
          }
          if (font !== 1 && font !== 4) {
            return 0;
          }
          if (font !== returnval) {
            this.io.mono ^= 4;
            this.format();
          }
          return returnval;
        },
        // Set styles
        set_style: function(stylebyte) {
          var io = this.io;
          if (stylebyte === 0) {
            io.reverse = io.bold = io.italic = 0;
            io.mono &= 254;
          }
          if (stylebyte & 1) {
            io.reverse = 1;
          }
          if (stylebyte & 2) {
            io.bold = 4;
          }
          if (stylebyte & 4) {
            io.italic = 2;
          }
          if (stylebyte & 8) {
            io.mono |= 1;
          }
          this.format();
        },
        // Set true colours
        set_true_colour: function(foreground, background) {
          const Glk = this.Glk;
          if (Glk.glk_gestalt(4352, 0)) {
            let fg, bg;
            if (foreground === 65534) {
              fg = -2;
            } else {
              if (foreground === 65535) {
                fg = -1;
              } else {
                fg = convert_true_colour(foreground);
              }
              this.io.fg = fg;
            }
            if (background === 65534) {
              bg = -2;
            } else {
              if (background === 65535) {
                bg = -1;
              } else {
                bg = convert_true_colour(background);
              }
              this.io.bg = bg;
            }
            Glk.garglk_set_zcolors_stream(this.mainwin.str, fg, bg);
            if (this.upperwin) {
              Glk.garglk_set_zcolors_stream(this.upperwin.str, fg, bg);
            }
          }
        },
        set_window: function(window) {
          this.io.currentwin = window;
          if (window) {
            this.set_cursor(0, 0);
          }
          this.Glk.glk_set_window(this.upperwin && window ? this.upperwin : this.mainwin);
          this.format();
        },
        split_window: function(lines) {
          var Glk = this.Glk, io = this.io, row = io.row, col = io.col, oldheight = io.height, str;
          io.height = lines;
          if (this.upperwin && lines > oldheight) {
            str = Glk.glk_window_get_stream(this.upperwin);
            while (oldheight < lines) {
              Glk.glk_window_move_cursor(this.upperwin, 0, oldheight++);
              Glk.glk_put_jstring_stream(str, Array(io.width + 1).join(" "));
            }
            Glk.glk_window_move_cursor(this.upperwin, col, row);
          }
          if (lines > io.maxheight) {
            io.maxheight = lines;
            if (!this.upperwin) {
              if (this.io.bg >= 0) {
                Glk.glk_stylehint_set(4, 0, 8, this.io.bg);
              }
              this.upperwin = Glk.glk_window_open(this.mainwin, 18, io.maxheight, 4, 203);
              if (this.Glk.glk_gestalt(4352, 0)) {
                Glk.garglk_set_zcolors_stream(this.upperwin.str, this.io.fg, this.io.bg);
              }
              Glk.glk_stylehint_clear(4, 0, 8);
            } else {
              Glk.glk_window_set_arrangement(Glk.glk_window_get_parent(this.upperwin), 18, io.maxheight, null);
            }
            io.glkheight = io.maxheight;
          }
          if (lines) {
            if (io.row >= lines) {
              this.set_cursor(0, 0);
            }
            if (this.version3) {
              Glk.glk_window_clear(this.upperwin);
            }
          }
        },
        // Update the header after restarting or restoring
        update_header: function() {
          var ram = this.ram;
          this.xorshift_seed = 0;
          this.update_screen_size();
          if (this.version3) {
            return ram.setUint8(
              1,
              ram.getUint8(1) & 143 | (this.statuswin ? 32 : 16) | 64
              // Variable pitch font is default - Or can we tell from options if the font is fixed pitch?
            );
          }
          ram.setUint8(
            1,
            (this.Glk.glk_gestalt(4352, 0) ? 1 : 0) | ram.getUint8(1) & 2 | 28 | 0
            // Timed input not supported yet
          );
          ram.setUint8(17, ram.getUint8(17) & 87);
          if (this.version > 4) {
            ram.setUint16(38, 257);
          }
          ram.setUint16(50, 258);
          this.extension_table(4, 0);
        },
        update_screen_size: function() {
          const Glk = this.Glk;
          const height_box = new Glk.RefBox();
          const width_box = new Glk.RefBox();
          const tempwin = Glk.glk_window_open(this.mainwin, 18, 0, 4, 0);
          let height = 0;
          let width = 0;
          Glk.glk_window_get_size(this.mainwin, width_box, height_box);
          height = height_box.get_value();
          if (this.upperwin) {
            Glk.glk_window_get_size(this.upperwin, width_box, height_box);
            height += height_box.get_value();
          }
          if (this.statuswin) {
            Glk.glk_window_get_size(this.statuswin, width_box, height_box);
            height += height_box.get_value();
          }
          if (tempwin) {
            Glk.glk_window_get_size(tempwin, width_box, 0);
            Glk.glk_window_close(tempwin);
          }
          width = width_box.get_value();
          height = Math.min(height, 254);
          width = this.io.width = Math.min(width, 255);
          if (this.version > 3) {
            this.ram.setUint8(32, height);
            this.ram.setUint8(33, width);
          }
          if (this.version > 4) {
            this.ram.setUint16(34, width);
            this.ram.setUint16(36, height);
          }
          if (this.io.col >= width) {
            this.io.col = width - 1;
          }
        },
        // Output the version 3 status line
        v3_status: function() {
          if (!this.statuswin) {
            return;
          }
          var Glk = this.Glk, str = Glk.glk_window_get_stream(this.statuswin), memory = this.m, width = this.io.width, hours_score = memory.getUint16(this.globals + 2), mins_turns = memory.getUint16(this.globals + 4), proptable = memory.getUint16(this.objects + 9 * memory.getUint16(this.globals) + 7), shortname = "" + this.decode(proptable + 1, memory.getUint8(proptable) * 2), rhs;
          if (memory.getUint8(1) & 2) {
            rhs = "Time: " + (hours_score % 12 === 0 ? 12 : hours_score % 12) + ":" + (mins_turns < 10 ? "0" : "") + mins_turns + " " + (hours_score > 11 ? "PM" : "AM");
          } else {
            rhs = "Score: " + hours_score + "  Turns: " + mins_turns;
          }
          Glk.glk_window_move_cursor(this.statuswin, 0, 0);
          Glk.glk_put_jstring_stream(str, Array(width + 1).join(" "));
          Glk.glk_window_move_cursor(this.statuswin, 0, 0);
          Glk.glk_put_jstring_stream(str, " " + shortname.slice(0, width - rhs.length - 4));
          Glk.glk_window_move_cursor(this.statuswin, width - rhs.length - 1, 0);
          Glk.glk_put_jstring_stream(str, rhs);
        }
      };
    }
  });

  // ../../../../../tmp/tmp_ui9n_e9/ifvms/src/zvm/disassembler.js
  var require_disassembler = __commonJS({
    "../../../../../tmp/tmp_ui9n_e9/ifvms/src/zvm/disassembler.js"(exports, module) {
      var AST = require_ast();
      module.exports.disassemble = function() {
        var pc, offset, memory = this.m, opcodes = this.opcodes, temp, code, opcode_class, operands_type, operands, context = new AST.RoutineContext(this, this.pc);
        function get_var_operand_types(operands_byte, operands_type2) {
          for (var i = 0; i < 4; i++) {
            operands_type2.push((operands_byte & 192) >> 6);
            operands_byte <<= 2;
          }
        }
        while (1) {
          offset = pc = this.pc;
          code = memory.getUint8(pc++);
          if (code === 190) {
            operands_type = -1;
            code = memory.getUint8(pc++) + 1e3;
          } else if (code & 128) {
            if (code & 64) {
              operands_type = -1;
              if (!(code & 32)) {
                code &= 31;
              }
            } else {
              operands_type = [(code & 48) >> 4];
              if (operands_type[0] < 3) {
                code &= 207;
              }
            }
          } else {
            operands_type = [code & 64 ? 2 : 1, code & 32 ? 2 : 1];
            code &= 31;
          }
          if (!opcodes[code]) {
            this.log("" + context);
            this.stop = 1;
            throw new Error("Unknown opcode #" + code + " at pc=" + offset);
          }
          opcode_class = opcodes[code].prototype;
          if (operands_type === -1) {
            operands_type = [];
            get_var_operand_types(memory.getUint8(pc++), operands_type);
            if (code === 236 || code === 250) {
              get_var_operand_types(memory.getUint8(pc++), operands_type);
            }
          }
          operands = [];
          temp = 0;
          while (temp < operands_type.length) {
            if (operands_type[temp] === 0) {
              operands.push(new AST.Operand(this, memory.getUint16(pc)));
              pc += 2;
            }
            if (operands_type[temp] === 1) {
              operands.push(new AST.Operand(this, memory.getUint8(pc++)));
            }
            if (operands_type[temp++] === 2) {
              operands.push(new AST.Variable(this, memory.getUint8(pc++)));
            }
          }
          if (opcode_class.storer) {
            operands.push(new AST.Variable(this, memory.getUint8(pc++)));
          }
          if (opcode_class.brancher) {
            temp = memory.getUint8(pc++);
            operands.push([
              temp & 128,
              // iftrue
              temp & 64 ? (
                // single byte address
                temp & 63
              ) : (
                // word address, but first get the second byte of it
                (temp << 8 | memory.getUint8(pc++)) << 18 >> 18
              )
            ]);
          }
          if (opcode_class.printer) {
            operands.push(pc);
            while (pc < this.eof) {
              temp = memory.getUint8(pc);
              pc += 2;
              if (temp & 128) {
                break;
              }
            }
          }
          this.pc = pc;
          context.ops.push(new opcodes[code](this, context, code, offset, pc, operands));
          temp = 0;
          if (opcode_class.stopper && !temp) {
            break;
          }
        }
        return context;
      };
    }
  });

  // ../../../../../tmp/tmp_ui9n_e9/ifvms/src/zvm.js
  var require_zvm = __commonJS({
    "../../../../../tmp/tmp_ui9n_e9/ifvms/src/zvm.js"(exports, module) {
      var utils = require_utils();
      var file = require_file();
      var default_options = {
        stack_len: 100 * 1e3,
        undo_len: 1e3 * 1e3
      };
      var api = {
        init: function() {
          this.jit = {};
          this.init = this.start;
        },
        prepare: function(storydata, options) {
          if (!options.Glk) {
            throw new Error("A reference to Glk is required");
          }
          this.Glk = options.Glk;
          this.data = storydata;
          this.options = utils.extend({}, default_options, options);
        },
        start: function() {
          var Glk = this.Glk, data;
          try {
            data = file.identify(this.data);
            delete this.data;
            if (!data || data.format !== "ZCOD") {
              throw new Error("This is not a Z-Code file");
            }
            if ([3, 4, 5, 8].indexOf(data.version) < 0) {
              throw new Error("Unsupported Z-Machine version: " + data.version);
            }
            this.m = utils.MemoryView(data.data);
            this.staticmem = this.m.getUint16(14);
            this.ram = utils.MemoryView(this.m, 0, this.staticmem);
            this.origram = this.m.getUint8Array(0, this.staticmem);
            let signature = "";
            let i = 0;
            while (i < 30) {
              signature += (this.origram[i] < 16 ? "0" : "") + this.origram[i++].toString(16);
            }
            this.signature = signature;
            let autorestored;
            const Dialog = this.options.Dialog;
            if (Dialog) {
              if (this.options.clear_vm_autosave) {
                Dialog.autosave_write(signature, null);
              } else if (this.options.do_vm_autosave) {
                try {
                  const snapshot = Dialog.autosave_read(signature);
                  if (snapshot) {
                    this.do_autorestore(snapshot);
                    autorestored = 1;
                  }
                } catch (ex) {
                  this.log("Autorestore failed, deleting it: " + ex);
                  Dialog.autosave_write(signature, null);
                }
              }
            }
            if (!autorestored) {
              this.restart();
              this.run();
            }
            if (!this.quit) {
              this.glk_event = new Glk.RefStruct();
              if (!this.glk_blocking_call) {
                Glk.glk_select(this.glk_event);
              } else {
                this.glk_event.push_field(this.glk_blocking_call);
              }
            }
            Glk.update();
          } catch (e) {
            Glk.fatal_error(e);
            console.log(e);
          }
        },
        resume: function(resumearg) {
          var Glk = this.Glk, glk_event = this.glk_event, event_type, run;
          try {
            event_type = glk_event.get_field(0);
            if (event_type === 2) {
              this.handle_char_input(glk_event.get_field(2));
              run = 1;
            }
            if (event_type === 3) {
              this.handle_line_input(glk_event.get_field(2), glk_event.get_field(3));
              run = 1;
            }
            if (event_type === 5) {
              this.update_screen_size();
            }
            if (event_type === "fileref_create_by_prompt") {
              run = this.handle_create_fileref(resumearg);
            }
            this.glk_blocking_call = null;
            if (run) {
              this.run();
            }
            if (!this.quit) {
              this.glk_event = new Glk.RefStruct();
              if (!this.glk_blocking_call) {
                Glk.glk_select(this.glk_event);
              } else {
                this.glk_event.push_field(this.glk_blocking_call);
              }
            }
            Glk.update();
          } catch (e) {
            Glk.fatal_error(e);
            console.log(e);
          }
        },
        get_signature: function() {
          return this.signature;
        },
        // Run
        run: function() {
          var pc, result;
          this.stop = 0;
          while (!this.stop) {
            pc = this.pc;
            if (!this.jit[pc]) {
              this.compile();
            }
            result = this.jit[pc](this);
            if (!isNaN(result)) {
              this.ret(result);
            }
          }
        },
        // Compile a JIT routine
        compile: function() {
          var context = this.disassemble();
          this.jit[context.pc] = new Function("e", "" + context);
          if (context.pc < this.staticmem) {
            this.log("Caching a JIT function in dynamic memory: " + context.pc);
          }
        }
      };
      var VM = utils.Class.subClass(utils.extend(
        api,
        require_runtime(),
        require_text(),
        require_io(),
        require_disassembler()
      ));
      module.exports = VM;
    }
  });
  return require_zvm();
})();
