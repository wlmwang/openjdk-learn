package com.sun.corba.se.PortableActivationIDL;

/**
* com/sun/corba/se/PortableActivationIDL/ORBAlreadyRegisteredHolder.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from /scratch/jenkins/workspace/8-2-build-olinux-amd64/jdk8u41/500/corba/src/share/classes/com/sun/corba/se/PortableActivationIDL/activation.idl
* Tuesday, January 14, 2020 11:00:47 PM PST
*/

public final class ORBAlreadyRegisteredHolder implements org.omg.CORBA.portable.Streamable
{
  public com.sun.corba.se.PortableActivationIDL.ORBAlreadyRegistered value = null;

  public ORBAlreadyRegisteredHolder ()
  {
  }

  public ORBAlreadyRegisteredHolder (com.sun.corba.se.PortableActivationIDL.ORBAlreadyRegistered initialValue)
  {
    value = initialValue;
  }

  public void _read (org.omg.CORBA.portable.InputStream i)
  {
    value = com.sun.corba.se.PortableActivationIDL.ORBAlreadyRegisteredHelper.read (i);
  }

  public void _write (org.omg.CORBA.portable.OutputStream o)
  {
    com.sun.corba.se.PortableActivationIDL.ORBAlreadyRegisteredHelper.write (o, value);
  }

  public org.omg.CORBA.TypeCode _type ()
  {
    return com.sun.corba.se.PortableActivationIDL.ORBAlreadyRegisteredHelper.type ();
  }

}
