package dk.i1.diameter.node;

import java.nio.ByteBuffer;

final class NormalConnectionBuffers extends ConnectionBuffers {
  private ByteBuffer in_buffer;
  private ByteBuffer out_buffer;

  NormalConnectionBuffers() {
    in_buffer = ByteBuffer.allocate(8192);
    out_buffer = ByteBuffer.allocate(8192);
  }

  @Override
  ByteBuffer netOutBuffer() {
    return out_buffer;
  }

  @Override
  ByteBuffer netInBuffer() {
    return in_buffer;
  }

  @Override
  ByteBuffer appInBuffer() {
    return in_buffer;
  }

  @Override
  ByteBuffer appOutBuffer() {
    return out_buffer;
  }

  @Override
  void processNetInBuffer() {
  }

  @Override
  void processAppOutBuffer() {
  }

  @Override
  void makeSpaceInNetInBuffer() {
    in_buffer = makeSpaceInBuffer(in_buffer, 4096);
  }

  @Override
  void makeSpaceInAppOutBuffer(final int how_much) {
    out_buffer = makeSpaceInBuffer(out_buffer, how_much);
  }
}
